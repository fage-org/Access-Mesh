# AccessMesh 前端镜像（T-ACCESS-047 改造自 pure-admin-thin 模板 Dockerfile）
# 两段式：node 阶段 pnpm 构建（外部用户免装 Node），nginx 阶段承载 dist + 同源反代配置。
# npm registry 可经 build-arg 覆盖：docker build --build-arg NPM_REGISTRY=https://registry.npmjs.org .
# node:24 对齐本地开发工具链（pnpm 11.5 在 node 20 报 ERR_UNKNOWN_BUILTIN_MODULE——
# 其依赖的内建模块 node 20 不具备，T-ACCESS-047 实证；engines 允许 >=22.13.0）
FROM node:24-alpine AS build-stage

ARG NPM_REGISTRY=https://registry.npmmirror.com

WORKDIR /app
# 钉住与本地开发一致的 pnpm 版本（pnpm@latest 漂移会改变构建脚本审批等行为）
RUN corepack enable && corepack prepare pnpm@11.5.0 --activate

RUN npm config set registry ${NPM_REGISTRY}

# pnpm-workspace.yaml 必须先于 install 就位——pnpm 11 的 allowBuilds 等设置住在该文件，
# 缺位时依赖构建脚本（esbuild/@parcel/watcher/vue-demi）被安全策略拦截，非交互环境直接报
# ERR_PNPM_IGNORED_BUILDS（T-ACCESS-047 实证）
COPY .npmrc pnpm-workspace.yaml package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:stable-alpine AS production-stage

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build-stage /app/dist /usr/share/nginx/html
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
