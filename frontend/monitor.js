const events = [];
let requests = 0, failures = 0, totalMs = 0;
const stats = document.querySelector('#monitor-stats');
const list = document.querySelector('#monitor-events');

function record(text) {
  events.unshift({ time: new Date().toLocaleTimeString('zh-CN'), text });
  events.splice(10);
  list.replaceChildren();
  for (const event of events) {
    const item = document.createElement('li');
    item.textContent = event.time + ' · ' + event.text;
    list.append(item);
  }
}

export function trackRequest(mode) {
  const start = performance.now();
  let finished = false;
  return success => {
    if (finished) return;
    finished = true;
    const elapsed = Math.round(performance.now() - start);
    requests++;
    if (!success) failures++;
    totalMs += elapsed;
    stats.textContent = '请求 ' + requests + ' 次 · 失败 ' + failures
      + ' 次 · 平均耗时 ' + Math.round(totalMs / requests) + ' ms';
    record(mode + ' · ' + (success ? '完成' : '失败') + ' · ' + elapsed + ' ms');
  };
}

// 不记录错误消息、URL、堆栈或用户输入，避免记录对话与密钥。
window.addEventListener('error', () => record('页面脚本或资源异常'));
window.addEventListener('unhandledrejection', () => record('未处理的异步异常'));
function networkStatus() {
  document.querySelector('#network-status').textContent = navigator.onLine
    ? '浏览器网络已连接（后端可用性以实际请求结果为准）' : '浏览器当前离线';
}
window.addEventListener('online', networkStatus);
window.addEventListener('offline', networkStatus);
networkStatus();
stats.textContent = '等待首次请求';
document.querySelector('#copyright-year').textContent = new Date().getFullYear();
