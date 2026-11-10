
import { fetchEventSource } from '@microsoft/fetch-event-source';

const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');
const workbenchUrl = path => apiBase + '/api/pet/workbench' + path;

async function checkResponse(response) {
  if (response.ok) return;
  const error = await response.json().catch(() => null);
  const message = typeof error?.message === 'string' && error.message.trim();
  throw new Error(message || '服务暂时无法完成请求（HTTP ' + response.status + '）');
}

function parseEvent(text) {
  try {
    return JSON.parse(text);
  } catch {
    throw new Error('收到无法读取的任务消息，请重新发送。');
  }
}

export async function runTurn({ chatId, message, image, signal, onProgress, onStep }) {
  const headers = { Accept: 'text/event-stream' };
  let body;
  if (image) {
    body = new FormData();
    body.append('chatId', chatId);
    body.append('message', message);
    body.append('image', image);
  } else {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify({ chatId, message });
  }

  let result;
  await fetchEventSource(workbenchUrl('/messages'), {
    method: 'POST',
    headers,
    body,
    signal,
    openWhenHidden: true,
    async onopen(response) {
      await checkResponse(response);
      if (!response.headers.get('content-type')?.includes('text/event-stream')) {
        throw new Error('后端未返回任务进度，请检查工作台接口是否已启动。');
      }
    },
    onmessage(event) {
      if (!event.data) return;
      if (event.event === 'progress') onProgress(parseEvent(event.data));
      if (event.event === 'step') onStep(parseEvent(event.data));
      if (event.event === 'result') result = parseEvent(event.data);
      if (event.event === 'error') {
        throw new Error(parseEvent(event.data)?.message || '任务执行失败。');
      }
    },
    onerror(error) { throw error; }
  });

  signal.throwIfAborted();
  if (typeof result?.answer !== 'string') {
    throw new Error('连接在收到答复前结束，可以检查网络后继续。');
  }
  return result;
}

export async function confirmPdf(chatId, actionId) {
  const response = await fetch(workbenchUrl('/actions/' + encodeURIComponent(actionId) + '/confirm'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ chatId })
  });
  await checkResponse(response);
  const artifact = await response.json();
  if (typeof artifact?.id !== 'string' || typeof artifact.title !== 'string') {
    throw new Error('服务未返回可下载的 PDF。');
  }
  return artifact;
}

export async function fetchPdf(chatId, artifactId) {
  const response = await fetch(workbenchUrl('/artifacts/' + encodeURIComponent(artifactId)), {
    headers: { 'X-Pet-Chat-Id': chatId }
  });
  await checkResponse(response);
  if (!response.headers.get('content-type')?.includes('application/pdf')) {
    throw new Error('服务未返回 PDF 文件。');
  }
  return response.blob();
}

