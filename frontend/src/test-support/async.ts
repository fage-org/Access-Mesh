/** 每次调用生成独立 Promise，供消费者显式控制响应先后。 */
export function defer<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/** 等待微任务及一个事件循环周期；不以固定余量表达业务时序。 */
export function flush() {
  return new Promise<void>(resolve => setTimeout(resolve));
}
