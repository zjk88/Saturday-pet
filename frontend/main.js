
import { trackRequest } from './monitor.js';
import { runTurn, confirmPdf, fetchPdf } from './workbench-api.js';

const $ = id => document.getElementById(id);
const storeKey = 'saturday-pet.workbench.v1';
const legacyKey = 'saturday-pet.sessions.v1';
const jobs = new Map();
const previews = new Map();
const editors = new Map();
const confirmations = new Map();
const downloadStates = new Map();
const narrow = matchMedia('(max-width: 940px)');
const mobile = matchMedia('(max-width: 650px)');
let contextOpen = !narrow.matches;
let menuOpen = false;
let photoTrigger;

function readStorage(key) {
  try {
    return JSON.parse(localStorage.getItem(key));
  } catch {
    return null;
  }
}

const stored = readStorage(storeKey);
const sessions = Array.isArray(stored?.sessions) ? stored.sessions.filter(session =>
  typeof session?.id === 'string' && Array.isArray(session.messages)) : [];
const oldSessions = readStorage(legacyKey)?.sessions;
const archive = (Array.isArray(oldSessions) ? oldSessions : []).flatMap(session => {
  if (!Array.isArray(session?.messages) || !session.messages.length) return [];
  const messages = session.messages.filter(item => typeof item?.text === 'string')
    .map(item => ({ role: item.label === '你' ? 'user' : 'assistant', text: item.text }));
  if (!messages.length) return [];
  return [{ id: 'archive:' + session.id, title: messages[0].text.slice(0, 18), messages, archived: true }];
});

function newSession() {
  const session = { id: crypto.randomUUID(), title: '新对话', messages: [] };
  sessions.unshift(session);
  return session;
}

let current = [...sessions, ...archive].find(session => session.id === stored?.activeId)
  || sessions[0] || newSession();

function save() {
  try {
    localStorage.setItem(storeKey, JSON.stringify({ activeId: current.id, sessions }));
  } catch {
    announce('浏览器无法保存完整历史，本页仍可继续使用。');
  }
}

function announce(text) {
  $('announcement').textContent = text;
}

const icon = name => '<svg aria-hidden="true"><use href="#i-' + name + '"></use></svg>';

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function button(label, className, handler, glyph) {
  const node = element('button', className);
  node.type = 'button';
  if (glyph) node.innerHTML = icon(glyph);
  node.append(label);
  node.onclick = handler;
  return node;
}

function setPanels() {
  $('context').hidden = !contextOpen;
  $('shell').classList.toggle('no-context', !contextOpen);
  $('context-toggle').setAttribute('aria-expanded', contextOpen);
  $('menu').setAttribute('aria-expanded', menuOpen);
  $('navrail').classList.toggle('visible', menuOpen);
  const overlay = menuOpen || (contextOpen && narrow.matches);
  $('scrim').classList.toggle('visible', overlay);
  $('main').inert = overlay;
  $('navrail').inert = overlay && !menuOpen;
}

function closePanels() {
  const focus = menuOpen ? $('menu') : $('context-toggle');
  if (menuOpen) menuOpen = false;
  else contextOpen = false;
  setPanels();
  focus.focus();
}

$('context-toggle').onclick = () => {
  contextOpen = !contextOpen;
  setPanels();
  if (contextOpen && narrow.matches) $('context-close').focus();
};
$('context-close').onclick = closePanels;
$('scrim').onclick = closePanels;
$('menu').onclick = () => {
  menuOpen = true;
  contextOpen = false;
  setPanels();
  $('new').focus();
};
narrow.onchange = () => {
  contextOpen = !narrow.matches;
  menuOpen = false;
  setPanels();
};
mobile.onchange = () => {
  if (!mobile.matches && menuOpen) {
    menuOpen = false;
    setPanels();
  }
};
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && !$('preview').open && (menuOpen || (narrow.matches && contextOpen))) {
    closePanels();
  }
});

function showPhoto(attachment) {
  const src = previews.get(attachment.id);
  if (!src) return;
  photoTrigger = document.activeElement;
  $('preview-title').textContent = attachment.name;
  $('preview-image').src = src;
  $('preview-image').alt = attachment.name;
  $('preview').showModal();
}

$('preview').onclose = () => {
  if (photoTrigger?.isConnected) photoTrigger.focus();
  $('preview-image').removeAttribute('src');
};

function editor() {
  if (!editors.has(current.id)) editors.set(current.id, { text: '', file: null, notice: '' });
  return editors.get(current.id);
}

function updateEditor() {
  const { file, notice } = editor();
  $('file-name').textContent = file?.name || '';
  $('attached').hidden = !file;
  $('notice').textContent = notice;
  $('notice').hidden = !notice;
}

function openSession(session) {
  current = session;
  menuOpen = false;
  $('message').value = editor().text;
  $('image').value = '';
  setPanels();
  render({ bottom: true });
  $('title').focus();
  save();
}

$('new').onclick = () => {
  const blank = sessions.find(session => !session.messages.length);
  openSession(blank || newSession());
};

$('message').oninput = event => { editor().text = event.target.value; };
$('remove-file').onclick = () => {
  Object.assign(editor(), { file: null, notice: '' });
  $('image').value = '';
  updateEditor();
  $('attach').focus();
};
$('attach').onclick = () => $('image').click();
$('image').onchange = () => {
  const file = $('image').files[0];
  $('image').value = '';
  if (!file) return;
  if (!['image/jpeg', 'image/png'].includes(file.type) || !file.size || file.size > 5 * 1024 * 1024) {
    editor().notice = '请选择不超过 5 MB 的 JPEG 或 PNG 图片。';
  } else {
    Object.assign(editor(), { file, notice: '' });
  }
  updateEditor();
};
$('stop').onclick = () => jobs.get(current.id)?.controller?.abort();

function renderHistory() {
  const items = [...sessions, ...archive].map(session => {
    const row = button('', '', () => openSession(session), 'chat');
    row.append(element('span', '', session.title || '未命名对话'));
    if (session.archived) row.title = '旧版会话记录';
    if (session === current) row.setAttribute('aria-current', 'true');
    return row;
  });
  $('history').replaceChildren(...items);
}

function toolLabel(name) {
  if (name === 'searchPetKnowledge') return '检索养宠知识';
  if (name === 'searchWeb') return '搜索网络资料';
  if (name === 'scrapeWebPage') return '读取网页资料';
  if (name === 'proposeDocumentDraft') return '整理文档草稿';
  if (typeof name === 'string' && (name.includes('amap_') || name.startsWith('maps_'))) return '查询高德地图';
  return '整理答复';
}

// Render a small subset of formatting with text nodes. Model and tool output never becomes HTML.
function richText(text) {
  const root = element('div', 'rich-text');
  const clean = line => line.replace(/\*\*([^*]+)\*\*/g, '$1');
  let list;
  let paragraphs = [];
  const flush = () => {
    if (paragraphs.length) root.append(element('p', 'response', clean(paragraphs.join('\n'))));
    paragraphs = [];
  };
  for (const line of text.split(/\r?\n/)) {
    const marker = line.match(/^\s*(?:([-*•])|(\d+)[.、])\s+(.+)/);
    if (marker) {
      flush();
      const tag = marker[2] ? 'ol' : 'ul';
      if (list?.localName !== tag) {
        list = element(tag);
        root.append(list);
      }
      list.append(element('li', '', clean(marker[3])));
      continue;
    }
    list = null;
    if (!line.trim()) {
      flush();
    } else if (/^#{1,6}\s/.test(line)) {
      flush();
      root.append(element('p', 'prose-heading', clean(line.replace(/^#{1,6}\s+/, ''))));
    } else {
      paragraphs.push(line);
    }
  }
  flush();
  return root;
}

function extractPlaces(content) {
  try {
    let data = JSON.parse(content);
    if (typeof data === 'string') data = JSON.parse(data);
    if (Array.isArray(data?.content)) {
      data = JSON.parse(data.content.find(item => typeof item?.text === 'string')?.text || '{}');
    }
    const entries = data?.pois || data?.data?.pois || data?.result?.pois;
    return Array.isArray(entries) ? entries.filter(place => typeof place?.name === 'string').slice(0, 5) : [];
  } catch {
    return [];
  }
}

function referenceCard(reference) {
  const map = toolLabel(reference.tool) === '查询高德地图';
  const names = {
    searchPetKnowledge: '查看知识库来源',
    searchWeb: '查看搜索结果',
    scrapeWebPage: '查看网页摘录'
  };
  const details = element('details', 'ref-card');
  const places = map ? extractPlaces(reference.content) : [];
  const label = map ? '高德地图结果' : names[reference.tool] || '查看参考资料';
  details.append(element('summary', '', label));
  if (places.length) {
    for (const place of places) {
      const item = element('div', 'map-item');
      const meta = [place.address, place.tel].filter(value => typeof value === 'string' && value);
      item.append(element('strong', '', place.name));
      if (meta.length) item.append(element('p', 'small', meta.join(' · ')));
      details.append(item);
    }
  } else {
    details.append(element('p', 'reference-text', reference.content));
  }
  return details;
}

function latestDraft(session) {
  return session.messages.findLast(item => item.draft);
}

function draftCard(item) {
  const draft = item.draft;
  const card = element('section', 'plan');
  const title = element('div', 'plan-title');
  const mark = element('div', 'plan-icon');
  mark.innerHTML = icon('list');
  title.append(mark, element('h3', '', draft.title), element('span', 'badge', item.artifact ? '已生成' : '草稿'));
  const content = element('div', 'plan-body');
  content.append(richText(draft.content));
  card.append(title, content);
  return card;
}

function draftActions(item) {
  const actionId = item.draft.actionId;
  const confirming = confirmations.get(current.id);
  const busy = jobs.has(current.id);
  if (confirming?.actionId !== actionId) {
    const row = element('div', 'actionline');
    const start = button('生成 PDF', 'button primary', () => {
      confirmations.set(current.id, { actionId, error: '' });
      render({ bottom: true });
      $('confirm-pdf')?.focus();
    }, 'list');
    start.disabled = busy;
    row.append(start);
    return row;
  }
  const card = element('section', 'confirm');
  const row = element('div', 'actionline');
  const approve = button(busy ? '正在生成…' : '确认生成 PDF', 'button primary',
    () => approvePdf(current, item), 'check');
  approve.id = 'confirm-pdf';
  approve.disabled = busy;
  const cancel = button('先不生成', 'button', () => {
    confirmations.delete(current.id);
    render({ bottom: true });
    $('title').focus();
  });
  cancel.disabled = busy;
  row.append(approve, cancel);
  card.append(element('strong', '', '生成这份 PDF？'),
    element('p', '', '将使用当前草稿。你也可以继续发消息调整内容。'), row);
  if (confirming.error) card.append(element('p', 'confirm-error', confirming.error));
  return card;
}

function artifactCard(item) {
  const card = element('section', 'file-card');
  const state = downloadStates.get(item.artifact.id);
  const row = element('div', 'actionline');
  const download = button(state?.busy ? '正在下载…' : '下载 PDF', 'button primary',
    () => downloadArtifact(current, item.artifact), 'download');
  download.disabled = Boolean(state?.busy);
  row.append(download);
  card.append(element('strong', '', item.artifact.title + '.pdf'),
    element('p', '', '临时文件，请及时下载。'), row);
  if (state?.error) card.append(element('p', 'file-error', state.error));
  return card;
}

function renderMessage(item) {
  if (item.role === 'user') {
    const row = element('article', 'user');
    const bubble = element('div', 'user-body');
    bubble.append(element('div', 'sr-only', '你'));
    if (item.attachment) {
      const attachment = item.attachment;
      bubble.append(previews.has(attachment.id)
        ? button(attachment.name, 'button attachment', () => showPhoto(attachment), 'image')
        : element('div', 'small', '图片 · ' + attachment.name));
    }
    bubble.append(element('p', '', item.text));
    row.append(bubble);
    return row;
  }

  const row = element('article', 'assistant');
  const avatar = element('div', 'agent-avatar');
  avatar.innerHTML = icon('paw');
  const body = element('div');
  const byline = element('div', 'byline');
  byline.append(element('strong', '', '小六'));
  if (item.statusLabel) byline.append(element('span', 'small', item.statusLabel));
  body.append(byline, richText(item.text));
  if (item.draft) {
    body.append(draftCard(item));
    if (!item.artifact && item === latestDraft(current)) body.append(draftActions(item));
  }
  for (const reference of item.references || []) body.append(referenceCard(reference));
  if (item.artifact) body.append(artifactCard(item));
  row.append(avatar, body);
  return row;
}



function section(label) {
  const block = element('section', 'context-block');
  const heading = element('div', 'context-title');
  heading.append(element('h3', '', label));
  block.append(heading);
  return block;
}

function renderContext() {
  const attachment = current.messages.findLast(item => item.attachment)?.attachment;
  const references = section('本轮引用');
  if (attachment) {
    const src = previews.get(attachment.id);
    if (src) {
      const photo = button('', 'photo', () => showPhoto(attachment));
      const image = element('img');
      image.src = src;
      image.alt = '';
      const label = element('div', 'photo-label');
      label.innerHTML = icon('image');
      label.append(element('strong', '', attachment.name));
      label.insertAdjacentHTML('beforeend', icon('arrow'));
      photo.append(image, label);
      references.append(photo);
    } else {
      references.append(element('p', 'small', attachment.name),
        element('p', 'small', '刷新后保留文件名，原图预览已清除。'));
    }
  } else {
    references.append(element('p', 'small', '这条对话还没有图片。'));
  }

  const timeline = section('任务进度');
  const job = jobs.get(current.id);
  const activity = job?.activity || current.activity || [];
  const lastDraft = latestDraft(current);
  const stages = [...activity];
  if (job) {
    stages.push({ label: job.label, waiting: true });
  } else if (lastDraft) {
    stages.push(lastDraft.artifact
      ? { label: 'PDF 已生成' }
      : { label: '草稿已就绪', detail: '等待你确认生成 PDF', waiting: true });
  }
  if (stages.length) {
    const list = element('ol', 'steps');
    for (const stage of stages) {
      const item = element('li');
      const mark = element('span', 'step-icon' + (stage.waiting || stage.incomplete ? ' wait' : ''));
      mark.innerHTML = icon(stage.waiting ? 'clock' : stage.incomplete ? 'close' : 'check');
      const description = element('div');
      description.append(element('strong', '', stage.label));
      if (stage.detail) description.append(element('p', '', stage.detail));
      item.append(mark, description);
      list.append(item);
    }
    timeline.append(list);
  } else {
    timeline.append(element('p', 'small', '小六开始处理任务后，步骤会在这里显示。'));
  }
  $('context-body').replaceChildren(references, timeline);
}

function renderSuggestions() {
  if (current.archived) {
    $('suggestions').replaceChildren(button('开始新对话', 'chip', () => $('new').click(), 'plus'));
    return;
  }

  const tasks = current.messages.length
    ? [[latestDraft(current) ? '把清单改成早晚两次' : '查养宠资料', latestDraft(current)
      ? '把刚才的清单改成早晚两次，先给我看新版草稿。' : '猫咪的饮水区怎么布置？请告诉我资料来源。', 'list'],
    ['查附近医院', '帮我查附近的宠物医院。', 'pin']]
    : [['整理一周照料清单', '帮我整理一周宠物照料清单，先给我看草稿。', 'list'],
      ['查养宠资料', '猫咪的饮水区怎么布置？请告诉我资料来源。', 'chat'],
      ['查附近医院', '帮我查附近的宠物医院。', 'pin']];
  $('suggestions').replaceChildren(...tasks.map(([label, prompt, glyph]) =>
    button(label, 'chip', () => {
      const input = $('message');
      if (input.value.trim()) {
        input.focus();
        return;
      }
      editor().text = prompt;
      input.value = prompt;
      input.focus();
    }, glyph)
  ));
}

function render({ bottom = false } = {}) {
  const thread = $('thread');
  const top = thread.scrollTop;
  const job = jobs.get(current.id);
  $('title').textContent = current.title;
  renderHistory();

  const flow = $('flow');
  if (current.messages.length) {
    flow.replaceChildren(...current.messages.map(renderMessage));
    if (current.archived) {
      flow.prepend(element('p', 'notice-inline', '这是旧版会话的历史记录。开始新对话即可使用统一工作台。'));
    }
  } else {
    const welcome = element('div', 'empty');
    const card = element('div');
    const logo = element('div', 'logo');
    logo.innerHTML = icon('paw');
    card.append(logo, element('h2', '', '今天想和小六聊什么？'),
      element('p', '', '可以从一张照片或一个养宠问题开始，也可以让小六整理照料计划、查找附近的宠物医院。'));
    welcome.append(card);
    flow.replaceChildren(welcome);
  }
  if (job?.controller) flow.append(renderMessage({ role: 'assistant', statusLabel: '处理中', text: job.label }));

  const box = $('composer').querySelector('.box');
  box.hidden = Boolean(current.archived);
  $('send').disabled = Boolean(job);
  $('send').hidden = Boolean(job?.controller);
  $('stop').hidden = !job?.controller;
  thread.setAttribute('aria-busy', Boolean(job?.controller));

  updateEditor();
  renderSuggestions();
  renderContext();
  thread.scrollTop = bottom ? thread.scrollHeight : top;
}

function updateJob(session, job, label) {
  if (job.label === '正在查看图片' && label !== job.label) job.activity.push({ label: '已读取图片' });
  job.label = label;
  if (session !== current) return;
  const running = $('flow').querySelector('.assistant:last-child .response');
  if (running) running.textContent = label;
  renderContext();
  announce(label);
}

function validDraft(draft) {
  return typeof draft?.actionId === 'string' && typeof draft.title === 'string' && typeof draft.content === 'string';
}

async function sendMessage(event) {
  event.preventDefault();
  const session = current;
  if (session.archived || jobs.has(session.id)) return;
  const input = $('message');
  const image = editor().file;
  const message = input.value.trim() || (image ? '请看看这张宠物照片。' : '');
  if (!message) {
    input.focus();
    return;
  }
  if (message.length > 4000) {
    editor().notice = '这一条消息请控制在 4000 字以内。';
    updateEditor();
    return;
  }

  const attachment = image ? { id: crypto.randomUUID(), name: image.name } : undefined;
  if (attachment) previews.set(attachment.id, URL.createObjectURL(image));
  if (!session.messages.length) session.title = message.slice(0, 18);
  session.messages.push({ role: 'user', text: message, attachment });
  confirmations.delete(session.id);
  Object.assign(editor(), { text: '', file: null, notice: '' });
  input.value = '';

  const job = { controller: new AbortController(), activity: [], label: image ? '正在接收图片…' : '正在准备答复…' };
  jobs.set(session.id, job);
  save();
  render({ bottom: true });
  const finish = trackRequest('小六对话');
  let complete = false;

  try {
    const result = await runTurn({
      chatId: session.id,
      message,
      image,
      signal: job.controller.signal,
      onProgress: event => {
        if (typeof event?.label === 'string') updateJob(session, job, event.label);
      },
      onStep: step => {
        const labels = Array.isArray(step?.tools) ? [...new Set(step.tools.map(toolLabel))] : [];
        job.activity.push({ label: labels.join('、') || '整理答复', detail: '第 ' + step.number + ' 步' });
        if (session === current) renderContext();
      }
    });
    const item = { role: 'assistant', text: result.answer };
    if (validDraft(result.draft)) item.draft = result.draft;
    if (Array.isArray(result.references)) {
      item.references = result.references.filter(ref => typeof ref?.tool === 'string' && typeof ref.content === 'string');
    }
    if (result.status === 'STEP_LIMIT') item.statusLabel = '未完成';
    session.messages.push(item);
    complete = result.status !== 'STEP_LIMIT';
    session.activity = [...job.activity, complete
      ? { label: '答复已就绪' }
      : { label: '达到步骤上限', incomplete: true }];
    if (session === current) announce('小六已返回答复。');
  } catch (error) {
    const aborted = job.controller.signal.aborted;
    const text = aborted
      ? '已停止等待。你可以继续发消息。'
      : error instanceof TypeError ? '无法连接工作台服务，请检查网络或后端是否启动。' : error.message || '这次请求未完成。';
    session.messages.push({ role: 'assistant', text, statusLabel: aborted ? '已停止' : '未完成' });
    session.activity = [...job.activity, { label: aborted ? '已停止等待' : '任务未完成', incomplete: true }];
    if (session === current) announce(text);
  } finally {
    finish(complete);
    jobs.delete(session.id);
    save();
    if (session === current) render({ bottom: true });
  }
}

$('composer').onsubmit = sendMessage;
$('message').onkeydown = event => {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) {
    event.preventDefault();
    $('composer').requestSubmit();
  }
};

async function approvePdf(session, item) {
  if (jobs.has(session.id) || item.artifact) return;
  const actionId = item.draft.actionId;
  if (confirmations.get(session.id)?.actionId !== actionId) return;
  jobs.set(session.id, { label: '正在生成 PDF…', activity: session.activity || [] });
  if (session === current) render();
  const finish = trackRequest('生成 PDF');
  try {
    item.artifact = await confirmPdf(session.id, actionId);
    confirmations.delete(session.id);
    finish(true);
    if (session === current) announce('PDF 已生成，可以下载。');
  } catch (error) {
    confirmations.set(session.id, { actionId, error: error.message || '暂时无法生成 PDF。' });
    finish(false);
  } finally {
    jobs.delete(session.id);
    save();
    if (session === current) render();
  }
}

async function downloadArtifact(session, artifact) {
  if (downloadStates.get(artifact.id)?.busy) return;
  downloadStates.set(artifact.id, { busy: true });
  if (session === current) render();
  const finish = trackRequest('下载 PDF');
  try {
    const pdf = await fetchPdf(session.id, artifact.id);
    const objectUrl = URL.createObjectURL(pdf);
    const link = element('a');
    link.href = objectUrl;
    const name = artifact.title.replace(/[\u0000-\u001f\\/:*?"<>|]/g, '-').slice(0, 110).trim();
    link.download = (name || '宠物照料清单') + '.pdf';
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    downloadStates.delete(artifact.id);
    finish(true);
  } catch (error) {
    downloadStates.set(artifact.id, { error: error.message || '下载失败。' });
    finish(false);
  } finally {
    if (session === current) render();
  }
}

setPanels();
render({ bottom: true });

