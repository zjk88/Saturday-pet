import { trackRequest } from './monitor.js';
import { fetchEventSource } from '@microsoft/fetch-event-source';

const modes = {
  chat: ['宠物对话', '问小六一个养宠问题，也可以接着上一轮聊。'],
  rag: ['宠物知识库', '从本地宠物知识资料中检索答案。'],
  image: ['看图识宠', '选择一张 JPEG 或 PNG 图片（最多 5 MB），告诉小六你想了解什么。'],
  mcp: ['查找宠物医院', '提供城市和附近地标，例如「上海静安寺附近有哪些宠物医院？」'],
  agent: ['多步任务', '可以附上宠物照片，让小六识别后查资料、整理建议或生成 PDF。图片支持 JPEG、PNG，最多 5 MB。']
};
const $ = selector => document.querySelector(selector);
let mode = 'chat', chatId, busy = false;
const storeKey = 'saturday-pet.sessions.v1';
let state = { mode, active: {}, sessions: [] };
try {
  const saved = JSON.parse(localStorage.getItem(storeKey));
  if (saved && modes[saved.mode] && saved.active && Array.isArray(saved.sessions)) state = saved;
} catch { /* Storage can be unavailable in private browsing. */ }

function save() {
  try { localStorage.setItem(storeKey, JSON.stringify(state)); }
  catch { $('#notice').textContent = '浏览器无法保存记录，本次会话仅在当前页面保留。'; }
}
function current() {
  return state.sessions.find(s => s.id === chatId);
}
function sessionList() {
  const list = $('#sessions');
  list.replaceChildren();
  for (const s of state.sessions.filter(s => s.mode === mode)) {
    const title = s.messages[0]?.text.slice(0, 16) || '新会话';
    list.add(new Option(title + ' · ' + s.id.slice(0, 8), s.id));
  }
  list.value = chatId;
}
function openSession(id, notice) {
  chatId = id;
  state.active[mode] = id;
  $('#history').replaceChildren();
  $('form').reset();
  for (const item of current().messages) bubble(item.label, item.text);
  $('#session-id').textContent = '会话 ID  ' + id;
  $('#notice').textContent = notice;
  sessionList();
  save();
}
function reset() {
  const id = crypto.randomUUID();
  state.sessions.unshift({ id, mode, messages: [] });
  openSession(id, '已开启新会话，可以发送第一条消息。');
}
$('#sessions').onchange = event => openSession(event.target.value, '已恢复此会话的记录。');
function select(key) {
  mode = state.mode = key;
  const id = state.active[key];
  if (state.sessions.some(s => s.id === id && s.mode === key)) openSession(id, '已打开此会话。');
  else reset();
  $('h1').textContent = modes[key][0];
  document.title = modes[key][0] + ' · Saturday Pet 小六宠物助手';
  $('#intro').textContent = modes[key][1];
  $('#image').hidden = !['image', 'agent'].includes(key);
  $('#image').required = key === 'image';
  document.querySelectorAll('nav button').forEach(b => b.setAttribute('aria-current', b.dataset.key === key));
}
for (const [key, [name]] of Object.entries(modes)) {
  const button = document.createElement('button');
  button.textContent = name;
  button.dataset.key = key;
  button.onclick = () => select(key);
  $('nav').append(button);
}
function bubble(label, text) {
  const box = document.createElement('article'), title = document.createElement('b'), body = document.createElement('span');
  title.textContent = label;
  body.textContent = text;
  box.append(title, body);
  $('#history').append(box);
  return body;
}
$('#new-session').onclick = reset;
$('form').onsubmit = async event => {
  event.preventDefault();
  const message = $('textarea').value.trim();
  if (!message || busy) return;
  const file = $('#image').files[0];
  if (file && file.size > 5 * 1024 * 1024) return alert('图片超过 5 MB');
  const finishTracking = trackRequest(modes[mode][0]);
  let success = false;
  busy = true;
  document.querySelectorAll('button').forEach(b => b.disabled = true);
  $('#sessions').disabled = true;
  current().messages.push({label: '你', text: message});
  sessionList();
  save();
  bubble('你', message);
  const reply = bubble('小六', '正在处理…');
  $('textarea').value = '';
  try {
    if (mode === 'agent' && file) {
      const body = new FormData();
      body.append('message', message);
      body.append('chatId', chatId);
      body.append('image', file);
      const response = await fetch('/api/pet/agent/run/image', {method: 'POST', body});
      if (!response.ok) throw Error('图片任务失败（HTTP ' + response.status + '）');
      const result = await response.json();
      reply.textContent = result.steps.map(step =>
        '第 ' + step.number + ' 步 · ' + (step.tools.join('、') || '整理答复')).join('\n')
        + '\n\n' + result.answer;
      $('#image').value = '';
    } else if (mode === 'chat' || mode === 'agent') {
      const path = mode === 'chat' ? 'chat/sse' : 'agent/run/sse';
      let output = '', completed = false;
      await fetchEventSource('/api/pet/' + path + '?' + new URLSearchParams({message, chatId}), {
        openWhenHidden: true,
        async onopen(r) { if (!r.ok || !r.headers.get('content-type')?.includes('text/event-stream')) throw Error('接口未返回事件流（HTTP ' + r.status + '）'); },
        onmessage(e) {
          if (!e.data) return;
          if (mode === 'chat') output += e.data;
          else if (e.event === 'step') {
            const step = JSON.parse(e.data);
            output += '第 ' + step.number + ' 步 · ' + (step.tools.join('、') || '整理答复') + '\n';
          } else if (e.event === 'result') {
            output += '\n' + JSON.parse(e.data).answer;
            completed = true;
          }
          reply.textContent = output;
          $('#history').scrollTop = $('#history').scrollHeight;
        },
        onclose() { if (!output || (mode === 'agent' && !completed)) throw Error('连接提前结束'); },
        onerror(error) { throw error; }
      });
    } else {
      const options = { method: 'POST' };
      let path = mode + '/chat';
      if (mode === 'image') {
        path = 'image/explain';
        options.body = new FormData();
        options.body.append('image', file);
        options.body.append('message', message);
      } else {
        options.headers = {'Content-Type':'application/json'};
        options.body = JSON.stringify({message, chatId});
      }
      const response = await fetch('/api/pet/' + path, options);
      const text = await response.text();
      if (!response.ok) {
        let reason;
        try { reason = JSON.parse(text).message; } catch { /* Generic HTTP error. */ }
        throw Error(reason || ('服务暂时无法完成请求（HTTP ' + response.status + '）'));
      }
      reply.textContent = text;
    }
    success = true;
  } catch(error) { if (reply.textContent === '正在处理…') reply.textContent = '';
    reply.append('请求未完成：' + error.message); }
  finally {
    finishTracking(success);
    current().messages.push({label: '小六', text: reply.textContent});
    save();
    $('#sessions').disabled = false;
    busy = false;
    document.querySelectorAll('button').forEach(b => b.disabled = false);
    $('#history').scrollTop = $('#history').scrollHeight;
  }
};
select(state.mode);

