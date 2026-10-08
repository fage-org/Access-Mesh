-- 生命周期协调协议：进程身份与状态读取位于同一条原子命令内。
-- 单主部署；缺少 INFO 权限或 Redis 不可达时调用失败，由入口拒绝访问。
local info = redis.call('INFO', 'server', 'replication', 'cluster')
local process = string.match(info, 'run_id:([^%s]+)')
local role = string.match(info, 'role:([^%s]+)')
local cluster = string.match(info, 'cluster_enabled:([^%s]+)')
local trusted = process and role == 'master' and cluster == '0'
local operation = ARGV[1]
local function read_state(key)
    if not trusted then return 'UNAVAILABLE|0' end
    local value = redis.pcall('GET', key)
    if not value or type(value) ~= 'string' then return 'UNAVAILABLE|0' end
    local storedProcess, status, token, epoch = string.match(value, '^([^|]+)|([^|]+)|([^|]+)|([^|]+)$')
    if storedProcess ~= process or not epoch or not string.match(epoch, '^[1-9][0-9]*$') then
        return 'UNAVAILABLE|0'
    end
    if status == 'ENABLED' or status == 'DISABLED' then return status .. '|' .. epoch end
    return 'UNAVAILABLE|0'
end
if operation == 'READ_BATCH' then
    local values = {}
    for i, key in ipairs(KEYS) do values[i] = read_state(key) end
    return values
end
if operation == 'READ' then return read_state(KEYS[1]) end
if operation == 'READ_SESSION' then return (process or '') .. '|' .. read_state(KEYS[1]) end
if not trusted then
    return 'UNAVAILABLE|0'
end
if operation == 'BLOCK' then
    redis.call('SET', KEYS[1], process .. '|BLOCKED|' .. ARGV[2] .. '|0')
    return process
end
if operation == 'PUBLISH' then
    if ARGV[2] ~= process then return 'REJECTED' end
    if ARGV[4] ~= 'ENABLED' and ARGV[4] ~= 'DISABLED' then return 'REJECTED' end
    if not string.match(ARGV[5], '^[1-9][0-9]*$') then return 'REJECTED' end
    local reserved = process .. '|BLOCKED|' .. ARGV[3] .. '|0'
    if redis.call('GET', KEYS[1]) ~= reserved then return 'REJECTED' end
    -- 只能消费仍存在的本次阻断令牌；缺失、被替换或已发布均不重建。
    redis.call('SET', KEYS[1], ARGV[2] .. '|' .. ARGV[4] .. '|' .. ARGV[3] .. '|' .. ARGV[5])
    return 'PUBLISHED'
end
if operation == 'DISCARD' then
    if ARGV[2] ~= process then return 'REJECTED' end
    local reserved = process .. '|BLOCKED|' .. ARGV[3] .. '|0'
    if redis.call('GET', KEYS[1]) ~= reserved then return 'REJECTED' end
    redis.call('DEL', KEYS[1])
    return 'DISCARDED'
end
return redis.error_reply('unknown tenant gate operation')
