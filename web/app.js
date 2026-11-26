let token = null, me = '', myName = '';
let curId = 'chung', curName = 'Nhóm Chung Toàn Khoa (SGU)', curIsGroup = true;
let contacts = [], groups = [], history = {}, reactions = {}, pinned = {}, convStates = {}, friends = [], friendRequests = [], starred = [];
let convFilter = 'all', mediaRecorder = null, audioChunks = [], drawerOpen = true, stickerTab = 'emoji';
let voiceTimerId = null, voiceSeconds = 0, voiceCancelled = false, pollTimerId = null;
let viewMode = 'chat', contactsTab = 'friends';
let mentionList = [], mentionIdx = 0, mentionStart = -1, replyingTo = null;

const AVATAR_COLORS = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899'];
function avColor(name) {
  let h = 0;
  for (let i = 0; i < (name || '').length; i++) h = (h * 31 + name.charCodeAt(i)) | 0;
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length];
}

function switchAuthTab(tab) {
  const isLogin = tab === 'login';
  document.getElementById('loginForm').style.display = isLogin ? 'block' : 'none';
  document.getElementById('registerForm').style.display = isLogin ? 'none' : 'block';
  document.getElementById('tabLoginBtn').classList.toggle('active', isLogin);
  document.getElementById('tabRegBtn').classList.toggle('active', !isLogin);
  document.getElementById('lstatus').innerText = '';
}

function addEmo(e) {
  const inp = document.getElementById('msgIn');
  inp.value += e;
  onMsgInput();
  inp.focus();
}

function onMsgInput() {
  const inp = document.getElementById('msgIn');
  const val = inp.value.trim();
  const btn = document.getElementById('btnMainSend');
  btn.innerText = val ? 'Gửi' : '👍';
  btn.classList.toggle('send-mode', !!val);
  updateMentionPopup(inp);
}

function updateMentionPopup(inp) {
  const pop = document.getElementById('mentionPopup');
  if (!pop) return;
  if (!curIsGroup) { pop.style.display = 'none'; mentionList = []; return; }
  const pos = inp.selectionStart ?? inp.value.length;
  const before = inp.value.slice(0, pos);
  const m = before.match(/(?:^|\s)@([^\s@]*)$/);
  if (!m) { pop.style.display = 'none'; mentionList = []; return; }
  mentionStart = pos - m[1].length - 1;
  const q = m[1].toLowerCase();
  const g = groups.find(x => x.id === curId);
  const memberIds = (g && g.members && g.members.length) ? g.members : contacts.map(u => u.username);
  mentionList = memberIds.filter(u => u !== me).map(u => {
    const c = contacts.find(x => x.username === u);
    return { username: u, displayName: (c && c.displayName) || u, avatarUrl: (c && c.avatarUrl) || '' };
  }).filter(u => u.displayName.toLowerCase().includes(q) || u.username.toLowerCase().includes(q));
  if (!mentionList.length) { pop.style.display = 'none'; return; }
  mentionIdx = 0;
  renderMentionPopup();
}

function renderMentionPopup() {
  const pop = document.getElementById('mentionPopup');
  pop.innerHTML = mentionList.map((u, i) => `
    <div class="mention-item ${i === mentionIdx ? 'active' : ''}" onmousedown="event.preventDefault();pickMention(${i})">
      <div class="mention-av" style="background:${avColor(u.displayName)}">${u.avatarUrl ? `<img src="${esc(u.avatarUrl)}">` : esc(u.displayName.slice(0, 2).toUpperCase())}</div>
      <span>${esc(u.displayName)}</span>
    </div>`).join('');
  pop.style.display = 'block';
}

function pickMention(idx) {
  const u = mentionList[idx];
  const inp = document.getElementById('msgIn');
  const pop = document.getElementById('mentionPopup');
  if (!u || !inp) return;
  const pos = inp.selectionStart ?? inp.value.length;
  const before = inp.value.slice(0, mentionStart);
  const after = inp.value.slice(pos);
  const inserted = '@' + u.displayName + ' ';
  inp.value = before + inserted + after;
  const nextPos = before.length + inserted.length;
  inp.setSelectionRange(nextPos, nextPos);
  pop.style.display = 'none';
  mentionList = [];
  onMsgInput();
  inp.focus();
}

function onMsgKeyDown(e) {
  const pop = document.getElementById('mentionPopup');
  const isOpen = pop && pop.style.display === 'block' && mentionList.length > 0;
  if (isOpen) {
    if (e.key === 'ArrowDown') { e.preventDefault(); mentionIdx = (mentionIdx + 1) % mentionList.length; renderMentionPopup(); return false; }
    if (e.key === 'ArrowUp') { e.preventDefault(); mentionIdx = (mentionIdx - 1 + mentionList.length) % mentionList.length; renderMentionPopup(); return false; }
    if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); pickMention(mentionIdx); return false; }
    if (e.key === 'Escape') { pop.style.display = 'none'; mentionList = []; return false; }
  }
  if (e.key === 'Enter') { e.preventDefault(); sendMsg(); return false; }
  return true;
}

function sendOrLike() {
  const val = document.getElementById('msgIn').value.trim();
  if (val) sendMsg();
  else { document.getElementById('msgIn').value = '👍'; sendMsg(); }
}

async function api(path, method = 'GET', bodyObj = null) {
  const headers = {};
  if (token) headers['X-Zalo-Token'] = token;
  if (bodyObj !== null && typeof bodyObj === 'object') headers['Content-Type'] = 'application/json';
  const opts = { method, headers };
  if (bodyObj !== null) opts.body = typeof bodyObj === 'string' ? bodyObj : JSON.stringify(bodyObj);
  return fetch(path, opts);
}

async function doLogin() {
  const u = document.getElementById('u').value.trim();
  const p = document.getElementById('p').value.trim();
  const res = await api('/api/login', 'POST', { username: u, password: p });
  const d = await res.json();
  if (!d.success) { document.getElementById('lstatus').innerText = d.message || 'Đăng nhập thất bại'; return; }
  applySessionData(d);
}

function applySessionData(d) {
  token = d.token; me = d.username; myName = d.displayName || me;
  sessionStorage.setItem('zalo_token', token);
  history = d.history || {}; reactions = d.reactions || {}; pinned = d.pinned || {};
  convStates = d.convStates || {}; friends = d.friends || []; friendRequests = d.friendRequests || [];
  starred = d.starred || [];
  if (d.users) contacts = d.users;
  groups = d.groups || [{ id: 'chung', name: 'Nhóm Chung Toàn Khoa (SGU)' }];

  updateMyAvatar(d.profile && d.profile.avatarUrl);
  document.documentElement.classList.remove('has-token');
  const bl = document.getElementById('bootLoader'); if (bl) bl.style.display = 'none';
  document.getElementById('loginPage').style.display = 'none';
  document.getElementById('app').style.display = 'flex';
  (d.events || []).forEach(handleEvent);
  updateBadges();
  renderConvList();
  renderHeaderAndDrawer();
  renderPinnedBar();
  renderMsgs();
  if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission();
  if (!pollTimerId) pollTimerId = setInterval(poll, 1200);
}

window.addEventListener('DOMContentLoaded', async () => {
  const saved = sessionStorage.getItem('zalo_token');
  if (!saved) return;
  token = saved;
  try {
    const res = await api('/api/resume', 'GET');
    if (res.ok) {
      const d = await res.json();
      if (d.success) { applySessionData(d); return; }
    }
  } catch (e) {}
  sessionStorage.removeItem('zalo_token');
  token = null;
  document.documentElement.classList.remove('has-token');
  const bl = document.getElementById('bootLoader'); if (bl) bl.style.display = 'none';
  document.getElementById('loginPage').style.display = 'flex';
});

function updateMyAvatar(avatarUrl) {
  const el = document.getElementById('myAvatar');
  if (avatarUrl) el.innerHTML = `<img src="${esc(avatarUrl)}">`;
  else el.innerText = myName.substring(0, 2).toUpperCase();
}

async function doRegister() {
  const dn = document.getElementById('regName').value.trim();
  const u = document.getElementById('regUser').value.trim();
  const p = document.getElementById('regPass').value.trim();
  const p2 = document.getElementById('regPass2').value.trim();
  const st = document.getElementById('lstatus');
  st.style.color = '#DC2626';
  if (!dn || !u || !p) { st.innerText = 'Vui lòng nhập đầy đủ họ tên, tên đăng nhập và mật khẩu!'; return; }
  if (p !== p2) { st.innerText = 'Mật khẩu xác nhận không khớp!'; return; }
  const res = await api('/api/register', 'POST', { username: u, password: p, displayName: dn });
  const d = await res.json();
  st.style.color = d.success ? '#16A34A' : '#DC2626';
  st.innerText = d.message;
  if (d.success) {
    setTimeout(() => {
      switchAuthTab('login');
      document.getElementById('u').value = u; document.getElementById('p').value = p;
      document.getElementById('lstatus').style.color = '#16A34A';
      document.getElementById('lstatus').innerText = 'Đăng ký thành công! Bấm Đăng nhập để vào Zalo.';
    }, 800);
  }
}

async function doLogout() {
  if (token) await api('/api/logout', 'POST');
  sessionStorage.removeItem('zalo_token');
  location.reload();
}

async function poll() {
  if (!token) return;
  const res = await api('/api/poll?token=' + encodeURIComponent(token));
  if (!res.ok) return;
  const d = await res.json();
  if (d.users) contacts = d.users;
  if (d.reactions) reactions = d.reactions;
  if (d.pinned) pinned = d.pinned;
  if (d.convStates) {
    Object.entries(d.convStates).forEach(([k, v]) => {
      if (!convStates[k]) convStates[k] = v;
      else {
        convStates[k].muted = v.muted;
        if (typeof v.unread === 'number' && v.unread > (convStates[k].unread || 0)) convStates[k].unread = v.unread;
      }
    });
  }
  if (d.friends) friends = d.friends;
  if (d.friendRequests) friendRequests = d.friendRequests;
  if (d.groups) groups = d.groups;
  (d.events || []).forEach(handleEvent);
  updateBadges();
  const cm = document.getElementById('convItemMenu');
  if (!cm || cm.style.display !== 'block') renderConvList();
  const pm = document.getElementById('pinnedMoreMenu');
  if (!pm || pm.style.display !== 'block') renderPinnedBar();
}

function updateBadges() {
  const reqB = document.getElementById('reqBadge');
  if (friendRequests.length > 0) { reqB.style.display = 'inline'; reqB.innerText = friendRequests.length; }
  else reqB.style.display = 'none';

  let totalUnread = 0;
  Object.values(convStates).forEach(s => { totalUnread += (s.unread || 0); });
  const chatB = document.getElementById('totalUnreadBadge');
  if (totalUnread > 0) { chatB.style.display = 'inline'; chatB.innerText = totalUnread > 99 ? '99+' : totalUnread; }
  else chatB.style.display = 'none';
}

function handleEvent(m) {
  if (m.type === 'login_result' && m.users) { contacts = m.users; return; }
  if (m.type === 'status_update' || m.type === 'user_list') { if (m.users) contacts = m.users; return; }
  if (m.type === 'typing_indicator') {
    if (m.username === curId || m.groupId === curId) {
      document.getElementById('typingLbl').innerText = '✍️ ' + (m.displayName || m.username) + ' đang soạn tin...';
      setTimeout(() => { document.getElementById('typingLbl').innerText = ''; }, 2500);
    }
    return;
  }
  if (m.type === 'recall_message') {
    Object.values(history).forEach(arr => arr.forEach(x => { if (x.messageId === m.messageId) { x.isRecalled = true; x.content = 'Tin nhắn đã được thu hồi'; } }));
    renderMsgs();
    return;
  }
  if (m.type === 'system') { pushMsg('chung', m); return; }
  if (m.type === 'my_cloud') { pushMsg('my_cloud', m); return; }
  if (m.type === 'group_message') { pushMsg(m.groupId || 'chung', m); notifyBrowser(m, m.groupId || 'chung'); return; }
  if (m.type === 'direct_message') { const cid = (m.username === me) ? m.toUser : m.username; pushMsg(cid, m); notifyBrowser(m, cid); return; }
  if (m.type === 'file_transfer') { const cid = m.groupId ? m.groupId : ((m.username === me) ? m.toUser : m.username); pushMsg(cid, m); notifyBrowser(m, cid); }
}

function notifyBrowser(m, cid) {
  if (m.username === me || cid === curId) return;
  if ((convStates[cid] || {}).muted) return;
  if ('Notification' in window && Notification.permission === 'granted') {
    new Notification(m.displayName || m.username, { body: m.fileName ? ('📎 ' + m.fileName) : (m.content || '') });
  }
}

function pushMsg(cid, m) {
  if (!history[cid]) history[cid] = [];
  if (m.messageId && history[cid].some(x => x.messageId === m.messageId)) return;
  history[cid].push(m);
  if (m.username && m.username !== me && (cid !== curId || viewMode !== 'chat')) {
    if (!convStates[cid]) convStates[cid] = { unread: 0, muted: false };
    convStates[cid].unread = (convStates[cid].unread || 0) + 1;
    updateBadges();
  }
  if (cid === curId && viewMode === 'chat') { renderMsgs(); renderRightDrawer(); }
  renderConvList();
}

function setConvFilter(f, btn) {
  convFilter = f;
  document.querySelectorAll('.ftab').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderConvList();
}

async function selectConv(id, name, isGrp) {
  curId = id; curName = name; curIsGroup = isGrp;
  document.getElementById('navChat').classList.toggle('active', id !== 'my_cloud');
  document.getElementById('navCloud').classList.toggle('active', id === 'my_cloud');
  document.getElementById('msgIn').placeholder = `Nhập @, tin nhắn tới ${name}`;
  if (convStates[id]) convStates[id].unread = 0;
  await api('/api/read', 'POST', { convId: id });
  updateBadges();
  renderHeaderAndDrawer();
  renderConvList();
  renderPinnedBar();
  renderMsgs();
}

function formatLastSeen(u) {
  if (!u) return 'Ngoại tuyến';
  if (u.online) return 'Đang hoạt động';
  const ts = Number(u.lastSeen || 0);
  if (!ts) return 'Ngoại tuyến';
  const diffSec = Math.max(0, Math.floor((Date.now() - ts) / 1000));
  if (diffSec < 60) return 'Vừa mới truy cập';
  if (diffSec < 3600) return `Hoạt động ${Math.floor(diffSec / 60)} phút trước`;
  if (diffSec < 86400) return `Hoạt động ${Math.floor(diffSec / 3600)} giờ trước`;
  const d = new Date(ts);
  return `Hoạt động ${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
}

function renderHeaderAndDrawer() {
  document.getElementById('chatTitle').innerText = curName;
  const av = document.getElementById('chatHeaderAvatar');
  const g = curIsGroup ? groups.find(x => x.id === curId) : null;
  const u = !curIsGroup && curId !== 'my_cloud' ? contacts.find(x => x.username === curId) : null;
  const grpAv = curIsGroup ? (localStorage.getItem('zalo_grp_av_' + curId) || '') : '';
  if (u && u.avatarUrl) av.innerHTML = `<img src="${esc(u.avatarUrl)}">`;
  else if (grpAv) av.innerHTML = `<img src="${esc(grpAv)}">`;
  else av.innerText = curId === 'my_cloud' ? '☁️' : curName.substring(0, 1).toUpperCase();
  av.style.background = curId === 'my_cloud' ? '#0068FF' : avColor(curName);
  document.getElementById('chatSub').innerText = curId === 'my_cloud' ? 'Lưu trữ cá nhân' : (g ? `${(g.members || []).length} thành viên` : formatLastSeen(u));
  renderRightDrawer();
}

function toggleRightDrawer() {
  drawerOpen = !drawerOpen;
  document.getElementById('rightDrawer').style.display = drawerOpen ? 'flex' : 'none';
  document.getElementById('btnToggleDrawer').classList.toggle('active-drawer', drawerOpen);
}

function renderRightDrawer() {
  if (!drawerOpen) return;
  const st = convStates[curId] || {};
  const muted = !!st.muted;
  const msgs = history[curId] || [];
  const imgs = msgs.filter(m => m.type === 'file_transfer' && !m.isRecalled && /\.(png|jpg|jpeg|gif|bmp|webp)$/i.test(m.fileName || ''));
  const files = msgs.filter(m => m.type === 'file_transfer' && !m.isRecalled && !/\.(png|jpg|jpeg|gif|bmp|webp)$/i.test(m.fileName || '') && !(m.fileName || '').startsWith('voice_'));

  const imgsHtml = imgs.slice(-8).map(m => {
    const u = m.fileData && m.fileData.startsWith('/api/files/') ? m.fileData : `data:image/*;base64,${m.fileData}`;
    return `<img src="${u}" onclick="openImageViewer('${u}','${esc(m.fileName)}')">`;
  }).join('') || '<div style="grid-column:1/-1;color:var(--muted);font-size:12px">Chưa có ảnh nào</div>';

  const filesHtml = files.slice(-4).map(m => {
    const u = m.fileData && m.fileData.startsWith('/api/files/') ? m.fileData : `data:application/octet-stream;base64,${m.fileData}`;
    const [tag, bg] = fileBadgeMeta(m.fileName);
    return `<div style="display:flex;align-items:center;gap:8px;padding:5px 0">
      <div class="file-badge" style="width:30px;height:32px;font-size:9px;background:${bg}">${tag}</div>
      <div style="flex:1;min-width:0"><a download="${esc(m.fileName)}" href="${u}" style="color:var(--text);text-decoration:none;font-size:12.5px;font-weight:600;display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(m.fileName)}</a>
      <div style="font-size:11px;color:var(--muted)">${Math.max(1,Math.round((m.fileSize||0)/1024))} KB</div></div>
    </div>`;
  }).join('') || '<div style="color:var(--muted);font-size:12px">Chưa có tệp nào</div>';

  const links = [];
  msgs.forEach(m => { const ms = (m.content || '').match(/https?:\/\/[^\s]+/g); if (ms) ms.forEach(u => links.push(u)); });
  const linksHtml = links.slice(-3).map(u => `<div style="padding:5px 0"><a href="${esc(u)}" target="_blank" style="color:var(--zalo);font-size:12.5px;text-decoration:none;display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(u)}</a></div>`).join('') || '<div style="color:var(--muted);font-size:12px">Chưa có link nào</div>';

  const memSec = curIsGroup ? (() => {
    const g = groups.find(x => x.id === curId) || { members: [], owner: 'system' };
    return `<div class="rd-section"><div class="rd-sec-title">Thành viên</div>
      <button class="prof-action-row" style="padding:4px 0" onclick="openMembersDrawer()">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#475569" stroke-width="1.8"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
        <span>${(g.members || []).length} thành viên</span>
      </button></div>`;
  })() : '';

  document.getElementById('rdBody').innerHTML = `
    <div class="rd-profile">
      <div class="av-circle rd-av" style="cursor:pointer;background:${curId==='my_cloud'?'#0068FF':avColor(curName)}" onclick="${!curIsGroup&&curId!=='my_cloud'?`openProfileModal('${esc(curId)}')`:''}">${curId==='my_cloud'?'☁️':esc(curName.substring(0,1).toUpperCase())}</div>
      <b style="font-size:15px">${esc(curName)}</b>
    </div>
    <div class="rd-quick">
      <button class="rd-qbtn" onclick="toggleMute(${!muted})"><span class="rd-qicon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg></span><span>${muted ? 'Bật TB' : 'Tắt thông báo'}</span></button>
      <button class="rd-qbtn" onclick="openStarredModal()"><span class="rd-qicon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg></span><span>Đã lưu (${starred.length})</span></button>
      <button class="rd-qbtn" onclick="openConvMenuModal()"><span class="rd-qicon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg></span><span>Quản lý</span></button>
      <button class="rd-qbtn" onclick="${curIsGroup?'openConvMenuModal()':(curId!=='my_cloud'?`openProfileModal('${esc(curId)}')`:'') }"><span class="rd-qicon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg></span><span>Thông tin</span></button>
    </div>
    ${memSec}
    <div class="rd-section"><div class="rd-sec-title">Ảnh/Video</div><div class="rd-media-grid">${imgsHtml}</div>${imgs.length > 0 ? `<button class="rd-see-all" onclick="openStorageDrawer('img')">Xem tất cả</button>` : ''}</div>
    <div class="rd-section"><div class="rd-sec-title">File</div>${filesHtml}${files.length > 0 ? `<button class="rd-see-all" onclick="openStorageDrawer('file')">Xem tất cả</button>` : ''}</div>
    <div class="rd-section"><div class="rd-sec-title">Link</div>${linksHtml}${links.length > 0 ? `<button class="rd-see-all" onclick="openStorageDrawer('link')">Xem tất cả</button>` : ''}</div>
    <div class="rd-section"><div class="rd-sec-title">Thiết lập bảo mật</div>
      <button class="rd-danger-row" onclick="clearChat()"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/></svg> Xoá lịch sử trò chuyện</button>
      ${curIsGroup && curId !== 'chung' ? `<button class="rd-danger-row" onclick="leaveGrp()"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg> Rời nhóm</button>` : ''}
    </div>`;
}

function renderPinnedBar() {
  const bar = document.getElementById('pinnedBar');
  const p = pinned[curId];
  if (p && p.messageId) {
    bar.style.display = 'flex';
    const preview = (p.content || '').replace(/^\[QUOTE:[^\]]+\]\s*/, '');
    bar.innerHTML = `
      <div class="pinned-left" onclick="scrollToMessage('${esc(p.messageId)}')" title="Nhấn để đi tới tin nhắn ghim">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#0068FF" stroke-width="1.8" style="flex-shrink:0"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/><line x1="8" y1="9" x2="16" y2="9"/><line x1="8" y1="13" x2="13" y2="13"/></svg>
        <div style="min-width:0">
          <div class="pinned-title">Tin nhắn</div>
          <div class="pinned-sub">${esc(p.pinnedBy ? p.pinnedBy + ': ' : '')}${esc(preview)}</div>
        </div>
      </div>
      <div class="pinned-right">
        ${curIsGroup ? `<button class="pinned-pill-btn" onclick="openPinBoardDrawer()">Bảng tin ⌄</button>` : ''}
        <button class="pinned-more-btn" onclick="togglePinnedMenu(event)" title="Tùy chọn"><svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="1.7"/><circle cx="12" cy="12" r="1.7"/><circle cx="19" cy="12" r="1.7"/></svg></button>
        <div id="pinnedMoreMenu" class="msg-more-menu" style="display:none;top:36px;bottom:auto;right:0;left:auto">
          <button onclick="copyMsgText('${esc(preview)}');document.getElementById('pinnedMoreMenu').style.display='none'">Copy</button>
          ${curIsGroup ? `<button onclick="document.getElementById('pinnedMoreMenu').style.display='none';openPinBoardDrawer()">Mở bảng tin cộng đồng</button>` : ''}
          <div class="msg-more-div"></div>
          <button style="color:#EF4444" onclick="unpinCurrent()">Bỏ ghim</button>
        </div>
      </div>`;
  } else {
    bar.style.display = 'none';
  }
}

function togglePinnedMenu(ev) {
  ev.stopPropagation();
  const m = document.getElementById('pinnedMoreMenu');
  if (m) m.style.display = m.style.display === 'block' ? 'none' : 'block';
}

function scrollToMessage(msgId) {
  if (!msgId) return;
  const el = document.querySelector(`[data-msg-id="${CSS.escape(msgId)}"]`);
  if (!el) return;
  el.scrollIntoView({ behavior: 'smooth', block: 'center' });
  const bubble = el.querySelector('.msg');
  if (bubble) {
    bubble.classList.add('msg-highlight');
    setTimeout(() => bubble.classList.remove('msg-highlight'), 2200);
  }
}

function openPinBoardDrawer() {
  drawerOpen = true;
  document.getElementById('rightDrawer').style.display = 'flex';
  document.getElementById('btnToggleDrawer').classList.add('active-drawer');
  const p = pinned[curId];
  const items = [];
  if (p && p.messageId) items.push({ messageId: p.messageId, sender: p.pinnedBy || 'Thành viên', content: p.content || '', time: p.timestamp || 'Hôm nay' });
  starred.filter(s => s.convId === curId).forEach(s => {
    if (!items.some(x => x.messageId === s.messageId)) items.push({ messageId: s.messageId, sender: s.senderName, content: s.content, time: s.timestamp || '' });
  });
  const cards = items.map(it => `<div class="post-item" style="background:#fff;margin-bottom:10px">
    <div style="font-weight:600;font-size:14px">${esc(it.sender)}</div>
    <div style="font-size:12px;color:var(--muted);margin-top:2px">Tin ghim</div>
    <div style="margin-top:8px;font-size:14px;color:var(--text)">${esc((it.content||'').replace(/^\[QUOTE:[^\]]+\]\s*/, ''))}</div>
    <div style="margin-top:8px;font-size:12.5px;color:var(--muted)">${esc(it.time)} | <a href="javascript:void(0)" style="color:var(--zalo);font-weight:500;text-decoration:none" onclick="scrollToMessage('${esc(it.messageId)}')">Xem tin nhắn gốc</a></div>
  </div>`).join('') || '<div style="color:var(--muted);font-size:13.5px;text-align:center;padding:24px 0">Chưa có tin ghim nào</div>';
  document.getElementById('rdBody').innerHTML = `<div style="padding:12px 14px;border-bottom:1px solid var(--border);display:flex;align-items:center;gap:8px">
    <button class="modal-back-btn" onclick="renderRightDrawer()">‹</button>
    <b style="font-size:15.5px;font-weight:600">Bảng tin cộng đồng</b>
  </div><div style="padding:12px 14px;background:#F4F5F7;min-height:100%">${cards}</div>`;
}

async function unpinCurrent() { await api('/api/pin', 'POST', { convId: curId, messageId: '', content: '' }); delete pinned[curId]; renderPinnedBar(); }

function getConvPref(key) {
  try { return JSON.parse(localStorage.getItem(key + '_' + me) || '[]'); } catch { return []; }
}
function setConvPref(key, arr) {
  try { localStorage.setItem(key + '_' + me, JSON.stringify(arr)); } catch {}
}

function renderConvList() {
  if (viewMode === 'contacts') { renderContactsLeftMenu(); renderContactsBody(); return; }
  const q = (document.getElementById('convSearch').value || '').trim().toLowerCase();
  const el = document.getElementById('convList');
  const pinnedConvs = getConvPref('zalo_conv_pins');
  const hiddenConvs = getConvPref('zalo_conv_hidden');
  const items = [];
  items.push({ id: 'my_cloud', name: 'Cloud của tôi', isGrp: false, online: true, defSub: 'Lưu trữ cá nhân' });
  groups.forEach(g => items.push({ id: g.id, name: g.name, isGrp: true, online: true, defSub: `${(g.members || []).length} thành viên` }));
  contacts.filter(u => u.username !== me).forEach(u => {
    items.push({ id: u.username, name: getAlias(u.username) || u.displayName || u.username, isGrp: false, online: u.online, defSub: friends.includes(u.username) ? 'Bạn bè' : 'Người dùng' });
  });

  const filtered = items.filter(it => {
    if (!q && hiddenConvs.includes(it.id)) return false;
    if (q && !it.name.toLowerCase().includes(q)) return false;
    if (convFilter === 'group' && !it.isGrp) return false;
    const unread = (convStates[it.id] && convStates[it.id].unread) || 0;
    if (convFilter === 'unread' && unread === 0) return false;
    return true;
  });

  filtered.sort((a, b) => {
    const pa = pinnedConvs.includes(a.id) ? 1 : 0;
    const pb = pinnedConvs.includes(b.id) ? 1 : 0;
    if (pa !== pb) return pb - pa;
    const ha = (history[a.id] || []).length;
    const hb = (history[b.id] || []).length;
    if ((ha > 0) !== (hb > 0)) return hb - ha;
    const ta = ha ? (history[a.id][ha - 1].timestamp || '') : '';
    const tb = hb ? (history[b.id][hb - 1].timestamp || '') : '';
    return tb.localeCompare(ta);
  });

  el.innerHTML = filtered.map(it => {
    const st = convStates[it.id] || {};
    const unread = st.unread || 0;
    const isPinned = pinnedConvs.includes(it.id);
    const arr = history[it.id] || [];
    const last = arr[arr.length - 1];
    const prev = last ? (last.isRecalled ? 'Tin nhắn đã được thu hồi' : (last.fileName ? '📎 ' + last.fileName : (last.content === '[FRIEND_OK]' ? 'Các bạn đã trở thành bạn bè' : (last.content || '').replace(/^\[QUOTE:[^\]]+\]\s*/, '')))) : it.defSub;
    const time = last ? (last.timestamp || '') : '';
    const uObj = !it.isGrp && it.id !== 'my_cloud' ? contacts.find(x => x.username === it.id) : null;
    const grpAv = it.isGrp ? (localStorage.getItem('zalo_grp_av_' + it.id) || '') : '';
    const init = it.id === 'my_cloud' ? '☁️' : (uObj && uObj.avatarUrl ? `<img src="${esc(uObj.avatarUrl)}">` : (grpAv ? `<img src="${esc(grpAv)}">` : esc(it.name.substring(0, 1).toUpperCase())));
    const pinIcon = isPinned ? `<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#64748B" stroke-width="2" style="vertical-align:-2px;margin-right:3px"><path d="M12 17v5"/><path d="M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z"/></svg>` : '';
    const muteIcon = st.muted ? `<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#94A3B8" stroke-width="2" style="vertical-align:-2px;margin-left:4px"><path d="M13.73 21a2 2 0 0 1-3.46 0"/><path d="M18.63 13A17.89 17.89 0 0 1 18 8"/><path d="M6.26 6.26A5.86 5.86 0 0 0 6 8c0 7-3 9-3 9h14"/><line x1="1" y1="1" x2="23" y2="23"/></svg>` : '';
    return `<div class="conv-item ${curId === it.id ? 'active' : ''}" onclick="selectConv('${it.id}','${esc(it.name)}',${it.isGrp})">
      <div class="av-wrap"><div class="av-circle" style="background:${it.id==='my_cloud'?'#0068FF':avColor(it.name)}">${init}</div>${it.online ? '<span class="dot-on"></span>' : ''}</div>
      <div class="conv-mid">
        <div class="conv-top">
          <span class="conv-name">${pinIcon}${esc(it.name)}${muteIcon}</span>
          <span style="display:flex;align-items:center">
            <button class="conv-more-btn" title="Tùy chọn" onclick="openConvItemMenu(event,'${esc(it.id)}')"><svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="1.7"/><circle cx="12" cy="12" r="1.7"/><circle cx="19" cy="12" r="1.7"/></svg></button>
            <span class="conv-time">${time}</span>
          </span>
        </div>
        <div class="conv-bot"><span class="conv-prev" style="${unread>0?'color:var(--text);font-weight:500':''}">${esc(prev)}</span>${unread > 0 ? `<span class="unread-pill">${unread}</span>` : ''}</div>
      </div>
    </div>`;
  }).join('');
}

function openConvItemMenu(ev, id) {
  ev.stopPropagation();
  let menu = document.getElementById('convItemMenu');
  if (!menu) {
    menu = document.createElement('div');
    menu.id = 'convItemMenu';
    menu.className = 'conv-more-menu';
    document.body.appendChild(menu);
  }
  const rect = ev.currentTarget.getBoundingClientRect();
  const pinnedConvs = getConvPref('zalo_conv_pins');
  const isPinned = pinnedConvs.includes(id);
  const gObj = groups.find(g => g.id === id);
  const gOwner = gObj ? ((gObj.owner && gObj.owner !== 'system') ? gObj.owner : ((gObj.members && gObj.members[0]) || '')) : '';
  const isGrpOwner = gObj && (gObj.owner === me || (gObj.owner === 'system' && me === gOwner));
  menu.style.left = Math.min(window.innerWidth - 200, rect.right - 20) + 'px';
  menu.style.top = (rect.bottom + 4) + 'px';
  menu.innerHTML = `
    <button onclick="togglePinConv('${esc(id)}')">${isPinned ? 'Bỏ ghim hội thoại' : 'Ghim hội thoại'}</button>
    <button onclick="hideConv('${esc(id)}')">Ẩn trò chuyện</button>
    ${gObj && id !== 'chung' ? (isGrpOwner ? `<button style="color:#EF4444" onclick="document.getElementById('convItemMenu').style.display='none';curId='${esc(id)}';disbandGrp()">Giải tán nhóm</button>` : `<button style="color:#EF4444" onclick="document.getElementById('convItemMenu').style.display='none';curId='${esc(id)}';leaveGrp()">Rời nhóm</button>`) : ''}
    <div class="msg-more-div"></div>
    <button style="color:#EF4444" onclick="deleteConvHistory('${esc(id)}')">Xóa hội thoại</button>`;
  menu.style.display = 'block';
}

function togglePinConv(id) {
  const arr = getConvPref('zalo_conv_pins');
  const idx = arr.indexOf(id);
  if (idx >= 0) arr.splice(idx, 1); else arr.unshift(id);
  setConvPref('zalo_conv_pins', arr);
  document.getElementById('convItemMenu').style.display = 'none';
  renderConvList();
}
function hideConv(id) {
  const arr = getConvPref('zalo_conv_hidden');
  if (!arr.includes(id)) arr.push(id);
  setConvPref('zalo_conv_hidden', arr);
  document.getElementById('convItemMenu').style.display = 'none';
  renderConvList();
}
async function deleteConvHistory(id) {
  await api('/api/clear-chat', 'POST', { convId: id });
  history[id] = [];
  document.getElementById('convItemMenu').style.display = 'none';
  renderConvList();
  if (curId === id) { renderMsgs(); renderRightDrawer(); }
}

function toggleMsgSearch() {
  const inp = document.getElementById('msgSearchIn');
  inp.style.display = inp.style.display === 'none' ? 'inline-block' : 'none';
  if (inp.style.display === 'none') { inp.value = ''; renderMsgs(); } else inp.focus();
}

function fileBadgeMeta(fname) {
  const l = (fname || '').toLowerCase();
  if (l.endsWith('.pdf')) return ['PDF', '#DC2626'];
  if (l.endsWith('.doc') || l.endsWith('.docx')) return ['DOC', '#2563EB'];
  if (l.endsWith('.xls') || l.endsWith('.xlsx')) return ['XLS', '#16A34A'];
  if (l.endsWith('.zip') || l.endsWith('.rar') || l.endsWith('.7z')) return ['ZIP', '#CA8A04'];
  return ['FILE', '#475569'];
}

function renderMsgs() {
  const body = document.getElementById('chatBody');
  const q = (document.getElementById('msgSearchIn').value || '').trim().toLowerCase();
  const arr = (history[curId] || []).filter(m => !q || (m.content || '').toLowerCase().includes(q) || (m.fileName || '').toLowerCase().includes(q));
  if (arr.length === 0) {
    const emptyText = curId === 'my_cloud' ? '☁️ Cloud của tôi — Lưu trữ ghi chú, hình ảnh, âm thanh và tệp tin cá nhân.' : 'Chưa có tin nhắn nào. Hãy gửi lời chào!';
    body.innerHTML = `<div class="msg sys">${emptyText}</div>`;
    return;
  }

  let prevSender = null;
  let html = '<div class="date-pill">Hôm nay</div>';
  html += arr.map((m, idx) => {
    if (m.type === 'system') { prevSender = null; return `<div class="msg sys">${esc(m.content || '')}</div>`; }
    if (m.content === '[FRIEND_OK]') {
      prevSender = null;
      const other = !curIsGroup ? (contacts.find(x => x.username === curId) || { displayName: curName, avatarUrl: '' }) : { displayName: m.displayName || m.username, avatarUrl: '' };
      const shown = getAlias(other.username || curId) || other.displayName || curName;
      return `<div class="friend-sys-pill"><span class="friend-sys-av" style="background:${avColor(shown)}">${other.avatarUrl ? `<img src="${esc(other.avatarUrl)}">` : esc(shown.slice(0,1).toUpperCase())}</span><span>Bạn vừa kết bạn với <b>${esc(shown)}</b></span></div>`;
    }
    const isMe = m.username === me;
    const isSamePrev = prevSender === m.username;
    prevSender = m.username;
    const nextMsg = arr[idx + 1];
    const isSameNext = nextMsg && nextMsg.type !== 'system' && nextMsg.username === m.username;

    const fileUrl = m.fileData && m.fileData.startsWith('/api/files/') ? m.fileData : (m.fileData ? `data:application/octet-stream;base64,${m.fileData}` : '');
    const fn = (m.fileName || '').toLowerCase();
    const isImg = m.type === 'file_transfer' && !m.isRecalled && /\.(png|jpg|jpeg|gif|bmp|webp)$/.test(fn);

    let content = '';
    if (m.isRecalled) content = `<i style="color:var(--muted)">${esc(m.content || 'Tin nhắn đã được thu hồi')}</i>`;
    else if (m.type === 'file_transfer' && fileUrl) {
      if (isImg) content = `<img class="preview" src="${fileUrl}" onclick="openImageViewer('${fileUrl}','${esc(m.fileName)}')">`;
      else if (/\.(mp4|webm|mov)$/.test(fn) && !fn.startsWith('voice_')) content = `<video controls src="${fileUrl}"></video><a download="${esc(m.fileName)}" href="${fileUrl}" style="font-size:12.5px;color:var(--zalo);text-decoration:none">Tải xuống ${esc(m.fileName)}</a>`;
      else if (/\.(mp3|wav|m4a|ogg)$/.test(fn) || fn.startsWith('voice_')) {
        const aid = 'aud_' + idx;
        const bars = [8,14,10,18,12,20,9,16,13,19,11,15,8,17,12,10].map(h => `<span class="vp-bar" style="height:${h}px"></span>`).join('');
        content = `<div class="voice-player" id="vp_${aid}">
          <button class="vp-btn" onclick="toggleVoicePlay('${aid}')" title="Phát tin nhắn thoại"><svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"/></svg></button>
          <div class="vp-wave">${bars}</div>
          <span class="vp-time" id="vt_${aid}">0:00</span>
          <audio id="${aid}" src="${fileUrl}" preload="metadata" onloadedmetadata="initVoiceMeta('${aid}')" ontimeupdate="updateVoiceTime('${aid}')" onended="endVoicePlay('${aid}')"></audio>
        </div>`;
      }
      else {
        const [tag, bg] = fileBadgeMeta(m.fileName);
        const dlSvg = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`;
        const eyeSvg = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>`;
        content = `<div class="file-card"><div class="file-badge" style="background:${bg}">${tag}</div>
          <div class="file-info"><div class="file-name">${esc(m.fileName)}</div><div class="file-sub">${Math.max(1, Math.round((m.fileSize || 0) / 1024))} KB • Nhấn để xem / tải</div></div>
          <a class="file-dl" href="${fileUrl}" target="_blank" title="Xem">${eyeSvg}</a>
          <a class="file-dl" download="${esc(m.fileName)}" href="${fileUrl}" title="Tải xuống">${dlSvg}</a></div>`;
      }
    } else {
      content = (!isMe && curIsGroup && !isSamePrev ? `<div class="msg-sender" onclick="openProfileModal('${esc(m.username)}')">${esc(m.displayName || m.username)}</div>` : '') + formatMsgText(m.content || '');
    }

    const reacts = reactions[m.messageId] || {};
    const reactHtml = Object.entries(reacts).map(([emo, cnt]) => `<span class="react-chip" onclick="reactMsg('${m.messageId}','${emo}')">${emo} ${cnt}</span>`).join('');
    const isStarred = starred.some(s => s.messageId === m.messageId);
    const rawPreview = (m.content || m.fileName || '').replace(/^\[QUOTE:[^\]]+\]\s*/, '').slice(0, 80);
    const moreId = 'more_' + idx;
    const reactTrigger = m.messageId && !m.isRecalled ? `<div class="react-trigger-wrap">${reactHtml}
      <div class="react-picker">${['👍','❤️','😆','😮','😢','😡'].map(e => `<button onclick="reactMsg('${m.messageId}','${e}')">${e}</button>`).join('')}</div>
      <button class="react-trigger-btn" onclick="reactMsg('${m.messageId}','👍')" title="Thả cảm xúc">👍</button>
    </div>` : '';
    const sideActions = m.messageId && !m.isRecalled ? `<div class="msg-side-actions">
      <button class="msg-side-btn" title="Trả lời" onclick="setReply('${esc(m.displayName||m.username)}','${esc(rawPreview)}',${isMe})"><svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M10 7H6a4 4 0 0 0-4 4v3h5v5h5v-7a5 5 0 0 0-2-5zm12 0h-4a4 4 0 0 0-4 4v3h5v5h5v-7a5 5 0 0 0-2-5z"/></svg></button>
      <button class="msg-side-btn" title="Chuyển tiếp" style="color:var(--zalo)" onclick="openForwardModal('${esc(rawPreview)}')"><svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M14 9V5l7 7-7 7v-4.1c-5 0-8.5 1.6-11 5.1 1-5 4-10 11-11z"/></svg></button>
      <button class="msg-side-btn" title="Thêm" onclick="toggleMsgMore(event,'${moreId}')"><svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="19" cy="12" r="1.8"/></svg></button>
      <div id="${moreId}" class="msg-more-menu" style="display:none">
        <button onclick="copyMsgText('${esc(rawPreview)}',this)"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg> Copy tin nhắn</button>
        <div class="msg-more-div"></div>
        <button onclick="pinMsg('${m.messageId}','${esc(rawPreview.slice(0,40))}')"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 17v5"/><path d="M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z"/></svg> Ghim tin nhắn</button>
        <button onclick="starMsg('${m.messageId}','${esc(m.displayName||m.username)}','${esc(rawPreview)}','${esc(m.timestamp||'')}')"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg> ${isStarred ? 'Bỏ đánh dấu' : 'Đánh dấu tin nhắn'}</button>
        ${isMe ? `<button onclick="recallMsg('${m.messageId}')"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg> Thu hồi</button>` : ''}
        <div class="msg-more-div"></div>
        <button style="color:#EF4444" onclick="hideMsg('${m.messageId}')"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#EF4444" stroke-width="1.8"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg> Xóa chỉ ở phía tôi</button>
      </div>
    </div>` : '';

    const sObj = !isMe ? contacts.find(x => x.username === m.username) : null;
    const avHtml = !isMe ? (isSamePrev ? '<div class="msg-av-spacer"></div>' : `<div class="msg-av" style="background:${avColor(m.displayName||m.username)}" onclick="openProfileModal('${esc(m.username)}')">${sObj && sObj.avatarUrl ? `<img src="${esc(sObj.avatarUrl)}">` : esc((m.displayName||m.username).substring(0,1).toUpperCase())}</div>`) : '';
    const showMeta = !isImg;
    const isLastMine = isMe && idx === arr.length - 1;
    return `<div class="msg-row ${isMe ? 'me' : ''} ${isSamePrev ? 'grouped' : ''}" data-msg-id="${esc(m.messageId || '')}"><div class="msg-col" style="gap:0">
      <div style="display:flex;align-items:flex-end;gap:8px;${isMe?'flex-direction:row-reverse':''}">
        ${avHtml}
        <div class="msg-wrap">
          <div class="msg ${isMe ? 'me' : ''} ${isImg ? 'img-only' : ''}" style="${reactHtml?'margin-bottom:8px':''}">${content}${showMeta ? `<div class="msg-meta"><span>${m.timestamp || ''}</span>${isStarred ? '<span>⭐</span>' : ''}</div>` : ''}${reactTrigger}</div>
          ${sideActions}
        </div>
      </div>
      ${isLastMine ? `<div class="sent-pill">✓ Đã gửi</div>` : ''}
    </div></div>`;
  }).join('');
  body.innerHTML = html;
  body.scrollTop = body.scrollHeight;
}

function toggleStickerPopup(ev, side = 'left') {
  ev.stopPropagation();
  const pop = document.getElementById('stickerPopup');
  const isOpen = pop.style.display === 'block';
  if (isOpen && pop.dataset.side === side) {
    pop.style.display = 'none';
    return;
  }
  pop.dataset.side = side;
  if (side === 'right') {
    pop.style.left = 'auto';
    pop.style.right = '16px';
    pop.style.bottom = '54px';
  } else {
    pop.style.right = 'auto';
    pop.style.left = '16px';
    pop.style.bottom = '96px';
  }
  pop.style.display = 'block';
  renderStickerGrid();
}
function switchStickerTab(tab) {
  stickerTab = tab;
  document.getElementById('spTabSticker').classList.toggle('active', tab === 'sticker');
  document.getElementById('spTabEmoji').classList.toggle('active', tab === 'emoji');
  renderStickerGrid();
}
function renderStickerGrid() {
  const g = document.getElementById('spGrid');
  if (stickerTab === 'emoji') {
    g.className = 'sp-grid';
    const emojis = ['😀','😂','😍','🥰','😎','😭','😡','🤔','😴','😤','🤯','😜','🤩','😳','😢','🙄','👍','👎','❤️','🔥','🎉','👏','🙏','💯','🤝','🥳','👀','💪','✨','🎁','😅','🤗','😇','🤐','🤢','🤑','🍺','📌','🎵','🚀'];
    g.innerHTML = emojis.map(e => `<button class="sp-emo" onclick="addEmo('${e}');document.getElementById('stickerPopup').style.display='none'">${e}</button>`).join('');
  } else {
    g.className = 'sp-grid stickers';
    const stks = [['👋 HI!','#E0F2FE'],['😂 HAHA','#FEF3C7'],['😍 YÊU','#FCE7F3'],['👍 OK!','#DCFCE7'],['😭 HUHU','#E0E7FF'],['🔥 ĐỈNH','#FFEDD5'],['🎉 CHÚC MỪNG','#F3E8FF'],['🙏 CẢM ƠN','#CCFBF1'],['❤️ TIM','#FEE2E2']];
    g.innerHTML = stks.map(([t,bg]) => `<button class="sp-stk" style="background:${bg}" onclick="document.getElementById('msgIn').value='${t}';sendMsg();document.getElementById('stickerPopup').style.display='none'">${t}</button>`).join('');
  }
}
document.addEventListener('click', e => {
  const pop = document.getElementById('stickerPopup');
  if (pop && pop.style.display === 'block' && !pop.contains(e.target)) pop.style.display = 'none';
  const sm = document.getElementById('settingsMenu');
  if (sm && sm.style.display === 'block' && !sm.contains(e.target)) sm.style.display = 'none';
  const pm = document.getElementById('pinnedMoreMenu');
  if (pm && pm.style.display === 'block' && !pm.contains(e.target)) pm.style.display = 'none';
  const cm = document.getElementById('convItemMenu');
  if (cm && cm.style.display === 'block' && !cm.contains(e.target)) cm.style.display = 'none';
  document.querySelectorAll('.msg-more-menu').forEach(m => { if (!m.contains(e.target)) m.style.display = 'none'; });
  document.querySelectorAll('.msg-side-actions.open').forEach(a => { if (!a.contains(e.target)) a.classList.remove('open'); });
});

function toggleSettingsMenu(ev) {
  ev.stopPropagation();
  const sm = document.getElementById('settingsMenu');
  sm.style.display = sm.style.display === 'block' ? 'none' : 'block';
}
function closeSettingsMenu() { document.getElementById('settingsMenu').style.display = 'none'; }

function showChatView() {
  viewMode = 'chat';
  document.getElementById('navChat').classList.add('active');
  document.getElementById('navFriends').classList.remove('active');
  document.getElementById('convFilterTabs').style.display = 'flex';
  document.getElementById('contactsView').style.display = 'none';
  document.getElementById('chatPaneMain').style.display = 'flex';
  document.getElementById('rightDrawer').style.display = drawerOpen ? 'flex' : 'none';
  renderConvList();
}

function showContactsView(tab = 'friends') {
  viewMode = 'contacts';
  contactsTab = tab;
  document.getElementById('navChat').classList.remove('active');
  document.getElementById('navCloud').classList.remove('active');
  document.getElementById('navFriends').classList.add('active');
  document.getElementById('convFilterTabs').style.display = 'none';
  document.getElementById('chatPaneMain').style.display = 'none';
  document.getElementById('rightDrawer').style.display = 'none';
  document.getElementById('contactsView').style.display = 'flex';
  renderContactsLeftMenu();
  renderContactsBody();
}

function renderContactsLeftMenu() {
  const items = [
    ['friends', 'Danh sách bạn bè'],
    ['groups', 'Danh sách nhóm và cộng đồng'],
    ['requests', `Lời mời kết bạn (${friendRequests.length})`],
    ['discover', 'Tìm thêm bạn bè']
  ];
  document.getElementById('convList').innerHTML = items.map(([k, lbl]) =>
    `<div class="cv-menu-item ${contactsTab===k?'active':''}" onclick="showContactsView('${k}')">${lbl}</div>`
  ).join('');
}

function renderContactsBody() {
  const titleEl = document.getElementById('cvTitle');
  const bodyEl = document.getElementById('cvBody');
  if (contactsTab === 'friends') {
    const list = contacts.filter(u => u.username !== me && friends.some(f => f.toLowerCase() === u.username.toLowerCase()));
    titleEl.innerText = `Danh sách bạn bè (${list.length})`;
    bodyEl.innerHTML = list.length ? `<div class="cv-card">${list.map(u => {
      const shown = getAlias(u.username) || u.displayName || u.username;
      const av = u.avatarUrl ? `<img src="${esc(u.avatarUrl)}">` : esc(shown.substring(0, 1).toUpperCase());
      return `<div class="cv-row">
        <div class="av-circle" style="cursor:pointer;background:${avColor(shown)}" onclick="openProfileModal('${esc(u.username)}')">${av}</div>
        <div style="flex:1;cursor:pointer" onclick="openProfileModal('${esc(u.username)}')"><b style="font-weight:600">${esc(shown)}</b><div style="font-size:12.5px;color:var(--muted)">${esc(formatLastSeen(u))}</div></div>
        <button class="btn-sec" style="width:auto;margin:0;background:#E5EFFF;color:var(--zalo)" onclick="showChatView();selectConv('${esc(u.username)}','${esc(shown)}',false)">Nhắn tin</button>
      </div>`;
    }).join('')}</div>` : '<div style="text-align:center;color:var(--muted);margin-top:60px">Bạn chưa có bạn bè nào. Hãy bấm <b>Tìm thêm bạn bè</b> để kết bạn!</div>';
  } else if (contactsTab === 'groups') {
    titleEl.innerText = `Danh sách nhóm và cộng đồng (${groups.length})`;
    bodyEl.innerHTML = `<div class="cv-card">${groups.map(g => `
      <div class="cv-row" style="cursor:pointer" onclick="showChatView();selectConv('${esc(g.id)}','${esc(g.name)}',true)">
        <div class="av-circle" style="background:${avColor(g.name)}">${esc(g.name.substring(0,1).toUpperCase())}</div>
        <div style="flex:1"><b style="font-weight:600">${esc(g.name)}</b><div style="font-size:12.5px;color:var(--muted)">${(g.members||[]).length} thành viên</div></div>
      </div>`).join('')}</div>`;
  } else if (contactsTab === 'requests') {
    titleEl.innerText = `Lời mời kết bạn (${friendRequests.length})`;
    bodyEl.innerHTML = friendRequests.length ? `<div class="cv-card">${friendRequests.map(r => `
      <div class="cv-row">
        <div class="av-circle" style="background:${avColor(r.displayName)}">${esc(r.displayName.substring(0,1).toUpperCase())}</div>
        <div style="flex:1"><b style="font-weight:600">${esc(r.displayName)}</b><div style="font-size:12.5px;color:var(--muted)">Muốn kết bạn với bạn</div></div>
        <button class="btn-primary" style="width:auto;margin:0;padding:7px 14px" onclick="respondReq('${r.username}',true)">Đồng ý</button>
        <button class="btn-sec" style="width:auto;margin:0;padding:7px 14px" onclick="respondReq('${r.username}',false)">Từ chối</button>
      </div>`).join('')}</div>` : '<div style="text-align:center;color:var(--muted);margin-top:80px">Bạn không có lời mời nào</div>';
  } else {
    const others = contacts.filter(u => u.username !== me && !friends.includes(u.username));
    titleEl.innerText = `Tìm thêm bạn bè (${others.length})`;
    bodyEl.innerHTML = others.length ? `<div class="cv-card">${others.map(u => {
      const av = u.avatarUrl ? `<img src="${esc(u.avatarUrl)}">` : esc((u.displayName || u.username).substring(0, 1).toUpperCase());
      return `<div class="cv-row">
        <div class="av-circle" style="cursor:pointer;background:${avColor(u.displayName||u.username)}" onclick="openProfileModal('${esc(u.username)}')">${av}</div>
        <div style="flex:1;cursor:pointer" onclick="openProfileModal('${esc(u.username)}')"><b style="font-weight:600">${esc(u.displayName||u.username)}</b><div style="font-size:12.5px;color:var(--muted)">Gợi ý kết bạn</div></div>
        <button class="btn-primary" style="width:auto;margin:0;padding:7px 14px" onclick="sendReq('${esc(u.username)}',this)">Kết bạn</button>
      </div>`;
    }).join('')}</div>` : '<div style="text-align:center;color:var(--muted);margin-top:60px">Đã kết bạn với tất cả mọi người.</div>';
  }
}

function pickFile(accept) {
  const inp = document.getElementById('fileIn');
  inp.accept = accept;
  inp.click();
}

async function sendMsg() {
  const inp = document.getElementById('msgIn');
  let txt = inp.value.trim();
  if (!txt) return;
  if (replyingTo) {
    txt = `[QUOTE:${replyingTo.sender}|${replyingTo.preview}]\n` + txt;
    cancelReply();
  }
  inp.value = '';
  onMsgInput();
  const payload = { type: curId === 'my_cloud' ? 'my_cloud' : (curIsGroup ? 'group_message' : 'direct_message'), content: txt, timestamp: new Date().toTimeString().slice(0, 5) };
  if (curIsGroup) payload.groupId = curId; else if (curId !== 'my_cloud') payload.toUser = curId;
  const res = await api('/api/send', 'POST', payload);
  const d = await res.json();
  payload.username = me; payload.displayName = myName; payload.messageId = d.messageId;
  pushMsg(curId, payload);
}

async function uploadBinaryFile(file, fileName) {
  return fetch('/api/upload?name=' + encodeURIComponent(fileName), { method: 'POST', headers: { 'X-Zalo-Token': token }, body: file }).then(r => r.json());
}

async function sendFile(input) {
  const f = input.files[0]; if (!f) return;
  if (f.size > 50 * 1024 * 1024) { alert('Tệp vượt quá 50MB!'); return; }
  const up = await uploadBinaryFile(f, f.name);
  if (up.ok) {
    const payload = { type: 'file_transfer', fileName: f.name, fileSize: f.size, fileData: up.url, timestamp: new Date().toTimeString().slice(0, 5) };
    if (curIsGroup) payload.groupId = curId; else if (curId !== 'my_cloud') payload.toUser = curId; else payload.toUser = me;
    const d = await api('/api/send', 'POST', payload).then(r => r.json());
    payload.username = me; payload.displayName = myName; payload.messageId = d.messageId;
    pushMsg(curId, payload);
  }
  input.value = '';
}

async function toggleVoiceRecord() {
  if (mediaRecorder && mediaRecorder.state === 'recording') { stopAndSendVoice(); return; }
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { alert('Vui lòng dùng HTTPS (https://localhost:8443) để ghi âm!'); return; }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    audioChunks = [];
    voiceCancelled = false;
    voiceSeconds = 0;
    mediaRecorder = new MediaRecorder(stream);
    mediaRecorder.ondataavailable = e => { if (e.data.size > 0) audioChunks.push(e.data); };
    mediaRecorder.onstop = async () => {
      stream.getTracks().forEach(t => t.stop());
      clearInterval(voiceTimerId);
      document.getElementById('btnVoice').classList.remove('recording');
      document.getElementById('voiceControls').style.display = 'none';
      if (voiceCancelled) return;
      const blob = new Blob(audioChunks, { type: 'audio/webm' });
      const fname = `voice_${Date.now()}.webm`;
      const up = await uploadBinaryFile(blob, fname);
      if (up.ok) {
        const payload = { type: 'file_transfer', fileName: fname, fileSize: blob.size, fileData: up.url, timestamp: new Date().toTimeString().slice(0, 5) };
        if (curIsGroup) payload.groupId = curId; else if (curId !== 'my_cloud') payload.toUser = curId; else payload.toUser = me;
        const d = await api('/api/send', 'POST', payload).then(r => r.json());
        payload.username = me; payload.displayName = myName; payload.messageId = d.messageId;
        pushMsg(curId, payload);
      }
    };
    mediaRecorder.start();
    document.getElementById('btnVoice').classList.add('recording');
    document.getElementById('voiceControls').style.display = 'inline-flex';
    document.getElementById('voiceTimer').innerText = '00:00';
    voiceTimerId = setInterval(() => {
      voiceSeconds++;
      const mm = String(Math.floor(voiceSeconds / 60)).padStart(2, '0');
      const ss = String(voiceSeconds % 60).padStart(2, '0');
      document.getElementById('voiceTimer').innerText = `${mm}:${ss}`;
    }, 1000);
  } catch (err) { alert('Không bật được Micro: ' + err.message); }
}

function stopAndSendVoice() {
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    voiceCancelled = false;
    mediaRecorder.stop();
  }
}

function cancelVoiceRecord() {
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    voiceCancelled = true;
    mediaRecorder.stop();
  }
}

async function reactMsg(msgId, emo) { const d = await api('/api/react', 'POST', { messageId: msgId, emoji: emo }).then(r => r.json()); if (d.ok) { reactions[msgId] = d.reactions; renderMsgs(); } }
async function recallMsg(msgId) {
  const payload = { type: 'recall_message', messageId: msgId };
  if (curIsGroup) payload.groupId = curId; else payload.toUser = curId;
  await api('/api/send', 'POST', payload);
  (history[curId] || []).forEach(x => { if (x.messageId === msgId) { x.isRecalled = true; x.content = 'Tin nhắn đã được thu hồi'; } });
  renderMsgs();
}
async function hideMsg(msgId) { await api('/api/hide-message', 'POST', { messageId: msgId }); history[curId] = (history[curId] || []).filter(x => x.messageId !== msgId); renderMsgs(); }
async function pinMsg(msgId, preview) { await api('/api/pin', 'POST', { convId: curId, messageId: msgId, content: preview }); pinned[curId] = { messageId: msgId, content: preview, pinnedBy: myName }; renderPinnedBar(); }
async function starMsg(msgId, senderName, content, timestamp) {
  const d = await api('/api/star', 'POST', { messageId: msgId, convId: curId, senderName, content, timestamp }).then(r => r.json());
  if (d.ok) { starred = d.list || []; renderMsgs(); renderRightDrawer(); }
}
function openStarredModal() {
  const rows = starred.map(s => `<div class="post-item"><div><b>${esc(s.senderName)}</b> <span style="font-size:11px;color:var(--muted)">${esc(s.timestamp)}</span></div><div style="margin-top:4px">${esc(s.content)}</div></div>`).join('') || '<div style="font-size:12px;color:var(--muted)">Chưa có tin nhắn nào được đánh dấu sao.</div>';
  showModal(`<div class="modal-head"><span>⭐ Tin nhắn đã đánh dấu sao (${starred.length})</span><button onclick="closeModal()">✕</button></div>${rows}`);
}

let _lbScale = 1;
function openImageViewer(url, name) {
  const imgs = (history[curId] || []).filter(m => m.type === 'file_transfer' && !m.isRecalled && /\.(png|jpg|jpeg|gif|bmp|webp)$/i.test(m.fileName || '')).map(m => ({
    url: m.fileData.startsWith('/api/files/') ? m.fileData : `data:image/*;base64,${m.fileData}`,
    name: m.fileName || 'image.png',
    sender: m.displayName || m.username || '',
    time: m.timestamp || ''
  }));
  if (!imgs.some(i => i.url === url)) imgs.unshift({ url, name: name || 'image.png', sender: '', time: '' });
  window._lbList = imgs;
  const idx = Math.max(0, imgs.findIndex(i => i.url === url));
  renderLightbox(idx);
}

function renderLightbox(idx) {
  const imgs = window._lbList || [];
  const cur = imgs[idx];
  if (!cur) return;
  _lbScale = 1;
  const lb = document.getElementById('imgLightbox');
  const rail = imgs.length > 1 ? `<div class="lb-rail">${imgs.map((x, i) => `<img src="${x.url}" class="${i===idx?'active':''}" onclick="renderLightbox(${i})">`).join('')}</div>` : '';
  lb.innerHTML = `
    <div class="lb-top"><span></span><span>${esc(curName)}</span><button onclick="closeLightbox()">✕</button></div>
    <div class="lb-main">
      <div class="lb-stage" onclick="if(event.target===this)closeLightbox()"><img id="lbImg" src="${cur.url}" alt="${esc(cur.name)}"></div>
      ${rail}
    </div>
    <div class="lb-bot">
      <div style="font-size:13.5px"><b>${esc(cur.sender || curName)}</b><div style="font-size:12px;color:#94A3B8">${esc(cur.time)}</div></div>
      <div class="lb-tools">
        <button onclick="zoomLightbox(0.2)" title="Phóng to"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg></button>
        <button onclick="zoomLightbox(-0.2)" title="Thu nhỏ"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="8" y1="11" x2="14" y2="11"/></svg></button>
        <a download="${esc(cur.name)}" href="${cur.url}" title="Tải xuống"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg> Tải xuống</a>
      </div>
      <div style="font-size:12.5px;color:#94A3B8">${esc(cur.name)}</div>
    </div>`;
  lb.style.display = 'flex';
}
function zoomLightbox(delta) {
  _lbScale = Math.min(3, Math.max(0.4, _lbScale + delta));
  const img = document.getElementById('lbImg');
  if (img) img.style.transform = `scale(${_lbScale})`;
}
function closeLightbox() { const lb = document.getElementById('imgLightbox'); if (lb) lb.style.display = 'none'; }

function getAlias(u) {
  try { const m = JSON.parse(localStorage.getItem('zalo_aliases_' + me) || '{}'); return m[u] || ''; } catch { return ''; }
}
function setAlias(u, alias) {
  try {
    const m = JSON.parse(localStorage.getItem('zalo_aliases_' + me) || '{}');
    if (alias && alias.trim()) m[u] = alias.trim(); else delete m[u];
    localStorage.setItem('zalo_aliases_' + me, JSON.stringify(m));
  } catch {}
}
function getUserMeta(u) {
  try { return JSON.parse(localStorage.getItem('zalo_meta_' + u) || '{}'); } catch { return {}; }
}

async function openProfileModal(targetUser) {
  const p = await api('/api/profile?user=' + encodeURIComponent(targetUser)).then(r => r.json());
  const isSelf = targetUser === me;
  const alias = !isSelf ? getAlias(targetUser) : '';
  const shownName = alias || p.displayName;
  const meta = getUserMeta(targetUser);
  const coverUrl = localStorage.getItem('zalo_cover_' + targetUser) || '';
  const coverStyle = coverUrl ? `background-image:url('${esc(coverUrl)}')` : '';
  const camSvg = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>`;
  const penSvg = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>`;
  const grpSvg = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#64748B" stroke-width="1.8"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`;
  const cardSvg = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#64748B" stroke-width="1.8"><rect x="2" y="4" width="20" height="16" rx="2"/><circle cx="8" cy="11" r="2"/><path d="M14 10h4"/><path d="M14 14h4"/><path d="M5 16c.5-1.5 1.8-2 3-2s2.5.5 3 2"/></svg>`;
  const blkSvg = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#64748B" stroke-width="1.8"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg>`;
  const mutualCount = !isSelf ? groups.filter(g => (g.members || []).includes(targetUser) && (g.members || []).includes(me)).length : 0;
  const curGrp = curIsGroup ? groups.find(g => g.id === curId) : null;
  const curGrpOwner = curGrp ? ((curGrp.owner && curGrp.owner !== 'system') ? curGrp.owner : ((curGrp.members && curGrp.members[0]) || '')) : '';
  const canKick = !isSelf && curGrp && ((curGrp.owner === me) || (curGrp.owner === 'system' && me === curGrpOwner)) && (curGrp.members || []).includes(targetUser);
  const hideInfo = !isSelf && !p.isFriend;
  const sharedImgs = !isSelf ? (history[targetUser] || []).filter(m => m.type === 'file_transfer' && !m.isRecalled && /\.(png|jpg|jpeg|gif|bmp|webp)$/i.test(m.fileName || '')).slice(-4) : [];
  const sharedImgHtml = sharedImgs.length
    ? `<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:6px;margin-top:10px">${sharedImgs.map(m => { const u = m.fileData.startsWith('/api/files/') ? m.fileData : `data:image/*;base64,${m.fileData}`; return `<img src="${u}" style="width:100%;height:68px;object-fit:cover;border-radius:4px;cursor:pointer" onclick="openImageViewer('${u}','${esc(m.fileName)}')">`; }).join('')}</div>`
    : `<div style="text-align:center;color:var(--muted);font-size:14px;padding:18px 0">Chưa có ảnh nào được chia sẻ</div>`;
  window._profCache = { p, meta };

  showModal(`<div class="modal-head"><span>Thông tin tài khoản</span><button onclick="closeModal()">✕</button></div>
    <div class="prof-cover" style="${coverStyle}">
      ${isSelf ? `<button class="prof-cover-btn" onclick="document.getElementById('coverUploadIn').click()">${camSvg} Đổi ảnh bìa</button><input id="coverUploadIn" type="file" accept="image/*" hidden onchange="uploadCover(this)">` : ''}
    </div>
    <div class="prof-top">
      <div class="prof-av-wrap">
        <div class="prof-av" style="background:${avColor(shownName)}">${p.avatarUrl ? `<img src="${esc(p.avatarUrl)}">` : esc(shownName.slice(0,2).toUpperCase())}</div>
        ${isSelf ? `<button class="prof-cam-btn" title="Đổi ảnh đại diện" onclick="document.getElementById('avUploadIn').click()">${camSvg}</button><input id="avUploadIn" type="file" accept="image/*" hidden onchange="uploadAvatar(this)">` : ''}
      </div>
      <div class="prof-name-row">
        <b style="font-size:19px;font-weight:600">${esc(shownName)}</b>
        <button class="prof-edit-icon" title="${isSelf ? 'Cập nhật thông tin cá nhân' : 'Đặt tên gợi nhớ'}" onclick="${isSelf ? 'openEditProfileView()' : `openAliasModal('${esc(p.username)}','${esc(p.displayName)}','${esc(p.avatarUrl||'')}')`}">${penSvg}</button>
      </div>
    </div>
    ${!isSelf ? `<div class="prof-actions">
      <button id="profFriendBtn" class="btn-sec" style="margin:0;font-size:14.5px" onclick="${p.isFriend?`unfriend('${esc(p.username)}')`:`sendReq('${esc(p.username)}', this)`}">${p.isFriend ? 'Bạn bè' : 'Kết bạn'}</button>
      <button class="btn-sec" style="margin:0;font-size:14.5px;background:#E5EFFF;color:var(--zalo)" onclick="closeModal();showChatView();selectConv('${esc(p.username)}','${esc(shownName)}',false)">Nhắn tin</button>
    </div>` : ''}
    <div class="prof-divider"></div>
    <div>
      <div style="font-weight:600;font-size:15.5px;margin-bottom:8px">Thông tin cá nhân</div>
      ${p.bio && !hideInfo ? `<div class="prof-info-row"><span class="prof-info-lbl">Giới thiệu</span><span class="prof-info-val">${esc(p.bio)}</span></div>` : ''}
      <div class="prof-info-row"><span class="prof-info-lbl">Giới tính</span><span class="prof-info-val">${hideInfo ? (meta.gender || '••••') : esc(meta.gender || 'Chưa cập nhật')}</span></div>
      <div class="prof-info-row"><span class="prof-info-lbl">Ngày sinh</span><span class="prof-info-val">${hideInfo ? '••/••/••••' : esc(meta.dobText || 'Chưa cập nhật')}</span></div>
      <div class="prof-info-row"><span class="prof-info-lbl">Điện thoại</span><span class="prof-info-val">${hideInfo ? '••••••••••' : '@' + esc(p.username)}</span></div>
    </div>
    ${isSelf ? `<button class="btn-sec" style="margin-top:14px;font-size:14.5px;padding:10px;display:flex;align-items:center;justify-content:center;gap:6px" onclick="openEditProfileView()">${penSvg} Cập nhật</button>` : `
      <div class="prof-divider"></div>
      <div><div style="font-weight:600;font-size:15.5px">Hình ảnh</div>${sharedImgHtml}</div>
      <div class="prof-divider"></div>
      <div class="prof-action-row" style="cursor:default;color:${mutualCount?'var(--text)':'var(--muted)'}">${grpSvg} <span>Nhóm chung (${mutualCount})</span></div>
      <div class="prof-action-row" style="cursor:default;color:var(--muted)">${cardSvg} <span>Chia sẻ danh thiếp</span></div>
      <button class="prof-action-row" onclick="toggleBlock('${esc(p.username)}',${!p.isBlockedByMe})">${blkSvg} <span>${p.isBlockedByMe ? 'Bỏ chặn tin nhắn và cuộc gọi' : 'Chặn tin nhắn và cuộc gọi'}</span></button>
      ${canKick ? `<button class="prof-action-row" style="color:#EF4444" onclick="closeModal();removeMember('${esc(p.username)}')">${blkSvg} <span>Xóa khỏi nhóm</span></button>` : ''}`}`);
}

function openEditProfileView() {
  const { p, meta } = window._profCache || { p: { displayName: myName, bio: '' }, meta: {} };
  const g = meta.gender || 'Nam';
  const d = meta.day || '01', m = meta.month || '01', y = meta.year || '2000';
  const opts = (from, to, sel) => Array.from({ length: to - from + 1 }, (_, i) => {
    const v = String(from + i).padStart(2, '0');
    return `<option value="${v}" ${v === String(sel) ? 'selected' : ''}>${v}</option>`;
  }).join('');
  showModal(`<div class="modal-head"><span style="display:flex;align-items:center"><button class="modal-back-btn" onclick="openProfileModal(me)">‹</button> Cập nhật thông tin cá nhân</span><button onclick="closeModal()">✕</button></div>
    <label>Tên hiển thị</label>
    <input id="nameIn" value="${esc(p.displayName || '')}" placeholder="Nhập tên hiển thị...">
    <label>Giới thiệu</label>
    <input id="bioIn" value="${esc(p.bio || '')}" placeholder="Nhập giới thiệu...">
    <div style="font-weight:600;font-size:15px;margin:14px 0 8px">Thông tin cá nhân</div>
    <div style="display:flex;gap:24px;align-items:center;font-size:14.5px;margin-bottom:10px">
      <label style="display:flex;align-items:center;gap:6px;margin:0;font-weight:400;cursor:pointer"><input type="radio" name="profGender" value="Nam" style="width:auto" ${g==='Nam'?'checked':''}> Nam</label>
      <label style="display:flex;align-items:center;gap:6px;margin:0;font-weight:400;cursor:pointer"><input type="radio" name="profGender" value="Nữ" style="width:auto" ${g==='Nữ'?'checked':''}> Nữ</label>
    </div>
    <label>Ngày sinh</label>
    <div class="dob-row">
      <select id="dobDay">${opts(1, 31, d)}</select>
      <select id="dobMonth">${opts(1, 12, m)}</select>
      <select id="dobYear">${opts(1960, 2020, y)}</select>
    </div>
    <div class="modal-footer-btns">
      <button class="btn-sec" style="width:auto;margin:0;padding:9px 18px" onclick="openProfileModal(me)">Hủy</button>
      <button class="btn-primary" style="width:auto;margin:0;padding:9px 20px" onclick="saveProfileInfo()">Cập nhật</button>
    </div>`);
}

function openAliasModal(username, origName, avatarUrl) {
  const curAlias = getAlias(username) || origName;
  showModal(`<div class="modal-head"><span style="display:flex;align-items:center"><button class="modal-back-btn" onclick="openProfileModal('${esc(username)}')">‹</button> Đặt tên gợi nhớ</span><button onclick="closeModal()">✕</button></div>
    <div style="text-align:center;padding:10px 0 6px">
      <div class="prof-av" style="margin:0 auto 12px;width:76px;height:76px;background:${avColor(origName)}">${avatarUrl ? `<img src="${esc(avatarUrl)}">` : esc(origName.slice(0,2).toUpperCase())}</div>
      <div style="font-size:14.5px;line-height:1.5;color:var(--text)">Hãy đặt cho <b>${esc(origName)}</b> một cái tên dễ nhớ.<br><span style="color:var(--muted);font-size:13.5px">Lưu ý: Tên gợi nhớ sẽ chỉ hiển thị riêng với bạn.</span></div>
    </div>
    <input id="aliasIn" value="${esc(curAlias)}" style="margin-top:14px" placeholder="Nhập tên gợi nhớ...">
    <div class="modal-footer-btns">
      <button class="btn-sec" style="width:auto;margin:0;padding:9px 18px" onclick="openProfileModal('${esc(username)}')">Hủy</button>
      <button class="btn-primary" style="width:auto;margin:0;padding:9px 20px" onclick="saveAlias('${esc(username)}')">Xác nhận</button>
    </div>`);
  setTimeout(() => { const el = document.getElementById('aliasIn'); if (el) { el.focus(); el.select(); } }, 20);
}

function saveAlias(username) {
  const val = (document.getElementById('aliasIn')?.value || '').trim();
  setAlias(username, val);
  renderConvList();
  renderHeaderAndDrawer();
  openProfileModal(username);
}

function openMembersDrawer() {
  drawerOpen = true;
  document.getElementById('rightDrawer').style.display = 'flex';
  document.getElementById('btnToggleDrawer').classList.add('active-drawer');
  const g = groups.find(x => x.id === curId) || { members: [], owner: 'system' };
  const isOwner = g.owner === me;
  document.getElementById('rdBody').innerHTML = `
    <div style="padding:12px 14px;border-bottom:1px solid var(--border);display:flex;align-items:center;gap:8px">
      <button class="modal-back-btn" onclick="renderRightDrawer()">‹</button>
      <b style="font-size:15.5px;font-weight:600">Thành viên</b>
    </div>
    <div style="padding:12px 14px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
        <span style="font-size:13.5px;font-weight:600;color:var(--text)">Danh sách thành viên (${(g.members||[]).length})</span>
        ${isOwner && curId !== 'chung' ? `<button class="mem-add-btn" onclick="openConvMenuModal()">+ Thêm</button>` : ''}
      </div>
      <input id="memSearchIn" placeholder="Tìm kiếm thành viên" oninput="renderMembersDrawerList()" style="padding:8px 12px;border-radius:18px;background:#EAEDF0;border:none;font-size:13.5px;margin-bottom:10px">
      <div id="memDrawerList"></div>
    </div>`;
  renderMembersDrawerList();
}

function renderMembersDrawerList() {
  const g = groups.find(x => x.id === curId) || { members: [], owner: 'system' };
  const q = (document.getElementById('memSearchIn')?.value || '').trim().toLowerCase();
  const ownerUser = (g.owner && g.owner !== 'system') ? g.owner : ((g.members && g.members[0]) || '');
  const isOwner = (g.owner === me) || (g.owner === 'system' && me === ownerUser);
  const sorted = [...(g.members || [])].sort((a, b) => (a === ownerUser ? -1 : b === ownerUser ? 1 : 0));
  const keySvg = `<svg width="9" height="9" viewBox="0 0 24 24" fill="currentColor"><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg>`;
  const dotsSvg = `<svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="1.7"/><circle cx="12" cy="12" r="1.7"/><circle cx="19" cy="12" r="1.7"/></svg>`;
  const rows = sorted.map(m => {
    const c = contacts.find(u => u.username === m);
    const name = getAlias(m) || (c && c.displayName) || m;
    if (q && !name.toLowerCase().includes(q)) return '';
    const isGrpOwner = m === ownerUser;
    const isMe = m === me;
    const isFr = friends.includes(m);
    const av = c && c.avatarUrl ? `<img src="${esc(c.avatarUrl)}">` : esc(name.slice(0, 2).toUpperCase());
    return `<div style="display:flex;align-items:center;gap:10px;padding:8px 0">
      <div class="av-wrap" style="cursor:pointer" onclick="openProfileModal('${esc(m)}')">
        <div class="av-circle" style="width:40px;height:40px;font-size:14px;background:${avColor(name)}">${av}</div>
        ${isGrpOwner ? `<span class="owner-key-badge" title="Trưởng nhóm">${keySvg}</span>` : ''}
      </div>
      <div style="flex:1;min-width:0;cursor:pointer" onclick="openProfileModal('${esc(m)}')">
        <div style="font-size:14px;font-weight:500;color:var(--text);white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(name)}</div>
        ${isGrpOwner ? `<div style="font-size:12px;color:var(--muted)">Trưởng nhóm</div>` : ''}
      </div>
      ${!isMe && !isFr ? `<button class="mem-add-btn" onclick="sendReq('${esc(m)}',this)">Kết bạn</button>` : ''}
      ${isOwner && !isMe ? `<button class="pinned-more-btn" title="Quản lý thành viên" onclick="openMemberActionMenu(event,'${esc(m)}','${esc(name)}')">${dotsSvg}</button>` : ''}
    </div>`;
  }).join('');
  const footer = curId !== 'chung' ? `<div style="margin-top:14px;padding-top:12px;border-top:1px solid var(--border)">
    ${isOwner ? `<button class="btn-danger" style="margin:0" onclick="disbandGrp()">Giải tán nhóm</button>` : `<button class="btn-danger" style="margin:0" onclick="leaveGrp()">Rời nhóm</button>`}
  </div>` : '';
  document.getElementById('memDrawerList').innerHTML = (rows || '<div style="text-align:center;color:var(--muted);padding:16px;font-size:13px">Không tìm thấy thành viên</div>') + footer;
}

function openMemberActionMenu(ev, username, displayName) {
  ev.stopPropagation();
  let menu = document.getElementById('convItemMenu');
  if (!menu) {
    menu = document.createElement('div');
    menu.id = 'convItemMenu';
    menu.className = 'conv-more-menu';
    document.body.appendChild(menu);
  }
  const rect = ev.currentTarget.getBoundingClientRect();
  menu.style.left = Math.min(window.innerWidth - 210, rect.right - 180) + 'px';
  menu.style.top = (rect.bottom + 4) + 'px';
  menu.style.width = '200px';
  menu.innerHTML = `
    <button onclick="document.getElementById('convItemMenu').style.display='none';transferOwnerDrawer('${esc(username)}')">Chuyển quyền trưởng nhóm</button>
    <div class="msg-more-div"></div>
    <button style="color:#EF4444" onclick="document.getElementById('convItemMenu').style.display='none';removeMember('${esc(username)}')">Xóa khỏi nhóm</button>`;
  menu.style.display = 'block';
}

async function transferOwnerDrawer(u) {
  await api('/api/groups/transfer-owner', 'POST', { groupId: curId, newOwner: u });
  await poll();
  openMembersDrawer();
}

async function removeMember(u) {
  await api('/api/groups/remove-member', 'POST', { groupId: curId, username: u });
  await poll();
  openMembersDrawer();
}

function openStorageDrawer(tab = 'img') {
  drawerOpen = true;
  document.getElementById('rightDrawer').style.display = 'flex';
  document.getElementById('btnToggleDrawer').classList.add('active-drawer');
  const msgs = history[curId] || [];
  let content = '';
  if (tab === 'img') {
    const list = msgs.filter(m => m.type === 'file_transfer' && !m.isRecalled && /\.(png|jpg|jpeg|gif|bmp|webp)$/i.test(m.fileName || '')).reverse();
    content = list.length
      ? `<div class="storage-grid">${list.map(m => { const u = m.fileData.startsWith('/api/files/') ? m.fileData : `data:image/*;base64,${m.fileData}`; return `<img src="${u}" onclick="openImageViewer('${u}','${esc(m.fileName)}')">`; }).join('')}</div>`
      : `<div style="text-align:center;color:var(--muted);padding:32px 12px;font-size:14px">Chưa có ảnh nào được chia sẻ</div>`;
  } else if (tab === 'file') {
    const list = msgs.filter(m => m.type === 'file_transfer' && !m.isRecalled && !/\.(png|jpg|jpeg|gif|bmp|webp)$/i.test(m.fileName || '')).reverse();
    content = list.length
      ? `<div style="padding:10px 14px">${list.map(m => { const u = m.fileData.startsWith('/api/files/') ? m.fileData : `data:application/octet-stream;base64,${m.fileData}`; const [tag, bg] = fileBadgeMeta(m.fileName); return `<div class="file-card" style="padding:8px 0;border-bottom:1px solid #F1F5F9"><div class="file-badge" style="background:${bg}">${tag}</div><div class="file-info"><div class="file-name">${esc(m.fileName)}</div><div class="file-sub">${Math.max(1, Math.round((m.fileSize||0)/1024))} KB • ${esc(m.timestamp||'')}</div></div><a class="file-dl" download="${esc(m.fileName)}" href="${u}" title="Tải xuống"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg></a></div>`; }).join('')}</div>`
      : `<div style="text-align:center;color:var(--muted);padding:32px 12px;font-size:14px">Chưa có file nào được chia sẻ</div>`;
  } else {
    const urls = [];
    msgs.forEach(m => { const ms = (m.content || '').match(/https?:\/\/[^\s]+/g); if (ms) ms.forEach(u => urls.push({ url: u, time: m.timestamp || '' })); });
    content = urls.length
      ? `<div style="padding:10px 14px">${urls.reverse().map(x => `<div class="post-item"><a href="${esc(x.url)}" target="_blank" style="color:var(--zalo);word-break:break-all">${esc(x.url)}</a><div style="font-size:11.5px;color:var(--muted);margin-top:4px">${esc(x.time)}</div></div>`).join('')}</div>`
      : `<div style="text-align:center;color:var(--muted);padding:32px 12px;font-size:14px">Chưa có link nào được chia sẻ</div>`;
  }
  document.getElementById('rdBody').innerHTML = `
    <div style="padding:12px 14px;border-bottom:1px solid var(--border);display:flex;align-items:center;gap:8px">
      <button class="modal-back-btn" onclick="renderRightDrawer()">‹</button>
      <b style="font-size:15.5px;font-weight:600">Kho lưu trữ</b>
    </div>
    <div class="storage-tabs">
      <button class="${tab==='img'?'active':''}" onclick="openStorageDrawer('img')">Ảnh/Video</button>
      <button class="${tab==='file'?'active':''}" onclick="openStorageDrawer('file')">Files</button>
      <button class="${tab==='link'?'active':''}" onclick="openStorageDrawer('link')">Links</button>
    </div>${content}`;
}
async function uploadAvatar(inp) { const f = inp.files[0]; if (!f) return; const up = await fetch('/api/upload?folder=avatars&name=' + encodeURIComponent(f.name), { method: 'POST', headers: { 'X-Zalo-Token': token }, body: f }).then(r => r.json()); if (up.ok) { await api('/api/profile', 'POST', { avatarUrl: up.url, bio: document.getElementById('bioIn')?.value || '' }); updateMyAvatar(up.url); openProfileModal(me); } }
async function uploadCover(inp) { const f = inp.files[0]; if (!f) return; const up = await fetch('/api/upload?folder=avatars&name=' + encodeURIComponent(f.name), { method: 'POST', headers: { 'X-Zalo-Token': token }, body: f }).then(r => r.json()); if (up.ok) { localStorage.setItem('zalo_cover_' + me, up.url); openProfileModal(me); } }
async function saveProfileInfo() {
  const displayName = (document.getElementById('nameIn')?.value || '').trim();
  const bio = document.getElementById('bioIn')?.value || '';
  const gender = document.querySelector('input[name="profGender"]:checked')?.value || 'Nam';
  const day = document.getElementById('dobDay')?.value || '01';
  const month = document.getElementById('dobMonth')?.value || '01';
  const year = document.getElementById('dobYear')?.value || '2000';
  localStorage.setItem('zalo_meta_' + me, JSON.stringify({ gender, day, month, year, dobText: `${day} tháng ${month}, ${year}` }));
  await api('/api/profile', 'POST', { avatarUrl: '', bio, displayName });
  if (displayName) myName = displayName;
  await poll();
  openProfileModal(me);
}

function openFriendsModal() { showContactsView('requests'); }
async function sendReq(u, btn) {
  const r = await api('/api/friends/request', 'POST', { toUser: u }).then(x => x.json());
  if (r.ok && btn) { btn.innerText = 'Đã gửi lời mời'; btn.disabled = true; }
  else if (!r.ok && btn) { btn.innerText = r.message || 'Đã gửi'; }
}
function onHeaderClick() {
  if (!curIsGroup && curId !== 'my_cloud') openProfileModal(curId);
  else if (curIsGroup) openConvMenuModal();
}

async function respondReq(u, ok) {
  await api('/api/friends/respond', 'POST', { fromUser: u, accept: ok });
  if (ok) {
    await api('/api/send', 'POST', { type: 'direct_message', toUser: u, content: '[FRIEND_OK]', timestamp: new Date().toTimeString().slice(0, 5) });
  }
  await poll();
  if (viewMode === 'contacts') renderContactsBody();
}

function openCreateGroupModal() {
  window._newGrpAvatar = '';
  const camSvg = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>`;
  showModal(`<div class="modal-head"><span>Tạo nhóm</span><button onclick="closeModal()">✕</button></div>
    <div style="display:flex;align-items:center;gap:12px;margin-bottom:14px">
      <button id="grpAvBtn" class="grp-av-pick" title="Chọn ảnh nhóm" onclick="document.getElementById('grpAvIn').click()">${camSvg}</button>
      <input id="grpAvIn" type="file" accept="image/*" hidden onchange="uploadGroupAvatar(this)">
      <input id="grpNameIn" class="grp-name-line" placeholder="Nhập tên nhóm...">
    </div>
    <input id="grpSearchIn" placeholder="Nhập tên để tìm kiếm..." oninput="renderCreateGroupList()" style="border-radius:20px;padding:9px 14px;background:#F8FAFC;margin-bottom:10px">
    <div style="font-weight:600;font-size:13.5px;margin-bottom:6px">Trò chuyện gần đây</div>
    <div id="grpChecks" style="max-height:240px;overflow-y:auto"></div>
    <div class="modal-footer-btns">
      <button class="btn-sec" style="width:auto;margin:0;padding:9px 18px" onclick="closeModal()">Hủy</button>
      <button class="btn-primary" style="width:auto;margin:0;padding:9px 20px" onclick="submitCreateGroup()">Tạo nhóm</button>
    </div>`);
  renderCreateGroupList();
}

function renderCreateGroupList() {
  const q = (document.getElementById('grpSearchIn')?.value || '').trim().toLowerCase();
  const checked = new Set([...document.querySelectorAll('#grpChecks input:checked')].map(c => c.value));
  const list = contacts.filter(u => u.username !== me).filter(u => {
    const name = (getAlias(u.username) || u.displayName || u.username).toLowerCase();
    return !q || name.includes(q);
  });
  document.getElementById('grpChecks').innerHTML = list.map(u => {
    const name = getAlias(u.username) || u.displayName || u.username;
    const av = u.avatarUrl ? `<img src="${esc(u.avatarUrl)}">` : esc(name.slice(0, 2).toUpperCase());
    return `<label class="cv-row" style="cursor:pointer;margin:0;padding:8px 4px;border:none">
      <input type="checkbox" class="round-check" value="${esc(u.username)}" ${checked.has(u.username)?'checked':''}>
      <div class="av-circle" style="width:38px;height:38px;font-size:14px;background:${avColor(name)}">${av}</div>
      <span style="font-weight:500;font-size:14.5px">${esc(name)}</span>
    </label>`;
  }).join('');
}

async function uploadGroupAvatar(inp) {
  const f = inp.files[0]; if (!f) return;
  const up = await fetch('/api/upload?folder=avatars&name=' + encodeURIComponent(f.name), { method: 'POST', headers: { 'X-Zalo-Token': token }, body: f }).then(r => r.json());
  if (up.ok) {
    window._newGrpAvatar = up.url;
    const btn = document.getElementById('grpAvBtn');
    if (btn) btn.innerHTML = `<img src="${esc(up.url)}">`;
  }
}

async function submitCreateGroup() {
  const name = document.getElementById('grpNameIn').value.trim();
  const members = [...document.querySelectorAll('#grpChecks input:checked')].map(c => c.value);
  if (!name) { document.getElementById('grpNameIn').focus(); return; }
  const res = await api('/api/groups/create', 'POST', { groupName: name, members }).then(r => r.json());
  if (res.ok) {
    if (window._newGrpAvatar) localStorage.setItem('zalo_grp_av_' + res.groupId, window._newGrpAvatar);
    closeModal(); await poll(); selectConv(res.groupId, res.groupName, true);
  }
}

async function openConvMenuModal() {
  const muted = !!(convStates[curId] || {}).muted;
  if (curIsGroup) {
    const g = groups.find(x => x.id === curId) || { members: [], owner: 'system' };
    const isOwner = g.owner === me;
    const memHtml = (g.members || []).map(m => `<div style="font-size:13px;padding:3px 0">• ${esc(m)} ${m === g.owner ? '<b>(Trưởng nhóm)</b>' : ''} ${isOwner && m !== me && curId !== 'chung' ? `<button onclick="transferOwner('${m}')">Chuyển quyền</button>` : ''}</div>`).join('');
    showModal(`<div class="modal-head"><span>Tùy chọn nhóm</span><button onclick="closeModal()">✕</button></div><b>Thành viên (${(g.members || []).length})</b>${memHtml}${isOwner && curId !== 'chung' ? `<label>Thêm thành viên</label><div style="display:flex;gap:6px"><input id="addMemIn"><button onclick="addMem()">Thêm</button></div>` : ''}<button class="btn-sec" onclick="toggleMute(${!muted})">${muted ? 'Bật thông báo' : 'Tắt thông báo'}</button><button class="btn-sec" onclick="clearChat()">Xóa đoạn chat phía tôi</button>${curId !== 'chung' ? `<button class="btn-danger" onclick="leaveGrp()">Rời nhóm</button>` : ''}${isOwner && curId !== 'chung' ? `<button class="btn-danger" onclick="disbandGrp()">Giải tán nhóm</button>` : ''}`);
  } else if (curId !== 'my_cloud') {
    const p = await api('/api/profile?user=' + encodeURIComponent(curId)).then(r => r.json());
    showModal(`<div class="modal-head"><span>Tùy chọn hội thoại</span><button onclick="closeModal()">✕</button></div><button class="btn-primary" onclick="openProfileModal('${esc(curId)}')">Xem trang cá nhân</button><button class="btn-sec" onclick="toggleMute(${!muted})">${muted ? 'Bật thông báo' : 'Tắt thông báo'}</button>${p.isFriend ? `<button class="btn-sec" onclick="unfriend('${esc(curId)}')">Hủy kết bạn</button>` : `<button class="btn-sec" onclick="sendReq('${esc(curId)}')">Kết bạn</button>`}<button class="btn-danger" onclick="toggleBlock('${esc(curId)}',${!p.isBlockedByMe})">${p.isBlockedByMe ? 'Gỡ chặn' : 'Chặn người này'}</button><button class="btn-danger" onclick="clearChat()">Xóa đoạn chat phía tôi</button>`);
  }
}

async function addMem() { await api('/api/groups/add-member', 'POST', { groupId: curId, username: document.getElementById('addMemIn').value.trim() }); await poll(); openConvMenuModal(); }
async function transferOwner(u) { await api('/api/groups/transfer-owner', 'POST', { groupId: curId, newOwner: u }); await poll(); openConvMenuModal(); }
async function leaveGrp() { const r = await api('/api/groups/leave', 'POST', { groupId: curId }).then(x => x.json()); if (!r.ok) alert(r.message); else { closeModal(); await poll(); selectConv('chung', 'Nhóm Chung Toàn Khoa (SGU)', true); } }
async function disbandGrp() { await api('/api/groups/disband', 'POST', { groupId: curId }); closeModal(); await poll(); selectConv('chung', 'Nhóm Chung Toàn Khoa (SGU)', true); }
async function unfriend(u) { await api('/api/friends/unfriend', 'POST', { targetUser: u }); await poll(); closeModal(); }
async function toggleBlock(u, b) { await api('/api/block', 'POST', { targetUser: u, block: b }); closeModal(); }
async function toggleMute(m) { await api('/api/mute', 'POST', { convId: curId, muted: m }); await poll(); closeModal(); }
async function clearChat() { await api('/api/clear-chat', 'POST', { convId: curId }); history[curId] = []; closeModal(); renderMsgs(); renderRightDrawer(); }
async function openSettingsModal() {
  const soundOn = localStorage.getItem('zalo_sound') !== '0';
  const enterSend = localStorage.getItem('zalo_enter_send') !== '0';
  showModal(`<div class="modal-head"><span>Cài đặt</span><button onclick="closeModal()">✕</button></div>
    <div style="display:flex;flex-direction:column;gap:14px;font-size:15px">
      <div style="font-weight:600;color:var(--text)">Thông báo &amp; Tin nhắn</div>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>Âm thanh khi có tin nhắn mới</span>
        <label class="zalo-switch" style="margin:0"><input type="checkbox" ${soundOn ? 'checked' : ''} onchange="localStorage.setItem('zalo_sound', this.checked ? '1' : '0')"><span class="zalo-slider"></span></label>
      </div>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>Nhấn Enter để gửi tin nhắn</span>
        <label class="zalo-switch" style="margin:0"><input type="checkbox" ${enterSend ? 'checked' : ''} onchange="localStorage.setItem('zalo_enter_send', this.checked ? '1' : '0')"><span class="zalo-slider"></span></label>
      </div>
      <div class="prof-divider" style="margin:4px -24px"></div>
      <div style="font-weight:600;color:var(--text)">Tài khoản</div>
      <div style="display:flex;justify-content:space-between;color:var(--muted);font-size:14.5px">
        <span>Tài khoản đang đăng nhập</span><b style="color:var(--text)">${esc(myName)}</b>
      </div>
      <div style="display:flex;gap:10px;margin-top:4px">
        <button class="btn-danger" style="margin:0;flex:1;font-size:14.5px;padding:10px" onclick="closeModal();doLogout()">Đăng xuất</button>
      </div>
    </div>`);
}

async function openSystemModal() {
  const st = await api('/api/status').then(r => r.json());
  const db = st.dbStats || {};
  showModal(`<div class="modal-head"><span>Trạng thái Hệ thống & Cơ sở dữ liệu</span><button onclick="closeModal()">✕</button></div>
    <div class="post-item" style="background:#F8FAFC;line-height:1.75">
      <div>🟢 <b>Trạng thái Server:</b> Đang hoạt động (${st.onlineCount} người online)</div>
      <div>🗄️ <b>Database SQLite:</b> <code>${esc(st.dbPath || 'data/zalo.db')}</code> (${db.users || 0} tài khoản, ${db.groups || 0} nhóm, ${db.messages || 0} tin nhắn)</div>
      <div>🔒 <b>TCP / TLS 1.3:</b> Cổng <code>${st.tlsPort}</code></div>
      <div>📡 <b>UDP Discovery:</b> Cổng <code>${st.udpDiscoveryPort}</code></div>
      <div>📢 <b>UDP Multicast:</b> Nhóm <code>${esc(st.multicastGroup)}</code></div>
      <div>🌐 <b>HTTP / HTTPS:</b> Cổng <code>8080</code> / <code>${st.httpsPort}</code></div>
      <div>📂 <b>FTP Server:</b> Cổng <code>${st.ftpPort}</code></div>
    </div>
    <label style="margin-top:14px">Phát thông báo toàn khoa (UDP Multicast → Nhóm Chung)</label>
    <input id="announceIn" placeholder="Ví dụ: Lịch thi cuối kỳ môn Lập trình mạng...">
    <button class="btn-primary" onclick="submitAnnounce()">📢 Phát thông báo ngay</button>`);
}

async function submitAnnounce() {
  const txt = document.getElementById('announceIn').value.trim();
  if (!txt) return;
  await api('/api/announce', 'POST', txt);
  closeModal();
  await selectConv('chung', 'Nhóm Chung Toàn Khoa (SGU)', true);
}
function fmtSec(s) {
  if (!isFinite(s) || s < 0) return '0:00';
  const m = Math.floor(s / 60), sec = Math.floor(s % 60);
  return `${m}:${String(sec).padStart(2, '0')}`;
}
function initVoiceMeta(id) {
  const a = document.getElementById(id), t = document.getElementById('vt_' + id);
  if (a && t && isFinite(a.duration)) t.innerText = fmtSec(a.duration);
}
function updateVoiceTime(id) {
  const a = document.getElementById(id), t = document.getElementById('vt_' + id);
  if (a && t) t.innerText = fmtSec(a.currentTime);
}
function endVoicePlay(id) {
  const vp = document.getElementById('vp_' + id), a = document.getElementById(id);
  if (vp) {
    vp.classList.remove('playing');
    const btn = vp.querySelector('.vp-btn');
    if (btn) btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"/></svg>`;
  }
  if (a && isFinite(a.duration)) { const t = document.getElementById('vt_' + id); if (t) t.innerText = fmtSec(a.duration); }
}
function toggleVoicePlay(id) {
  const a = document.getElementById(id), vp = document.getElementById('vp_' + id);
  if (!a || !vp) return;
  const btn = vp.querySelector('.vp-btn');
  if (a.paused) {
    document.querySelectorAll('.voice-player.playing audio').forEach(o => { if (o !== a) o.pause(); });
    a.play();
    vp.classList.add('playing');
    if (btn) btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>`;
  } else {
    a.pause();
    vp.classList.remove('playing');
    if (btn) btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"/></svg>`;
  }
}

function showModal(html) { document.getElementById('modalContent').innerHTML = html; document.getElementById('modalOverlay').style.display = 'flex'; }
function closeModal() { document.getElementById('modalOverlay').style.display = 'none'; }
function formatMsgText(raw) {
  let text = raw || '';
  let quoteHtml = '';
  const qm = text.match(/^\[QUOTE:([^|\]]+)\|([^\]]*)\]\s*([\s\S]*)$/);
  if (qm) {
    quoteHtml = `<div class="msg-quote"><b>${esc(qm[1])}</b><span>${esc(qm[2])}</span></div>`;
    text = qm[3];
  }
  let safe = esc(text);
  contacts.forEach(u => {
    const tag = '@' + esc(u.displayName);
    if (safe.includes(tag)) {
      safe = safe.split(tag).join(`<span class="msg-mention" onclick="openProfileModal('${esc(u.username)}')">${tag}</span>`);
    }
  });
  return quoteHtml + safe;
}

function setReply(sender, preview, isMeSender) {
  replyingTo = { sender, preview };
  document.getElementById('replySender').innerText = 'Trả lời ' + sender;
  document.getElementById('replyText').innerText = preview;
  document.getElementById('replyBar').style.display = 'flex';
  const inp = document.getElementById('msgIn');
  if (curIsGroup && !isMeSender && !inp.value.startsWith('@' + sender)) {
    inp.value = '@' + sender + ' ' + inp.value;
    onMsgInput();
  }
  inp.focus();
}

function copyMsgText(text, btn) {
  navigator.clipboard?.writeText(text);
  document.querySelectorAll('.msg-more-menu').forEach(m => m.style.display = 'none');
}

function openForwardModal(text) {
  window._fwdText = text;
  window._fwdTab = 'all';
  window._fwdSelected = new Set();
  showModal(`<div class="modal-head"><span>Chia sẻ</span><button onclick="closeModal()">✕</button></div>
    <input id="fwdSearch" placeholder="Tìm kiếm..." oninput="renderForwardList()" style="margin-bottom:10px">
    <div class="storage-tabs" style="margin:0 -24px 8px;padding:0 16px">
      <button id="fwdTabAll" class="active" onclick="window._fwdTab='all';renderForwardList()">Gần đây</button>
      <button id="fwdTabGrp" onclick="window._fwdTab='group';renderForwardList()">Nhóm trò chuyện</button>
      <button id="fwdTabFr" onclick="window._fwdTab='friend';renderForwardList()">Bạn bè</button>
    </div>
    <div id="fwdList" style="max-height:240px;overflow-y:auto;margin:0 -6px"></div>
    <div style="background:#F4F5F7;padding:10px 12px;border-radius:6px;font-size:13.5px;margin-top:10px">
      <div style="font-weight:600;font-size:12.5px;color:var(--muted);margin-bottom:3px">Chia sẻ tin nhắn</div>
      <div style="color:var(--text);white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(text)}</div>
    </div>
    <input id="fwdNote" placeholder="Nhập tin nhắn..." style="margin-top:10px">
    <div class="modal-footer-btns">
      <button class="btn-sec" style="width:auto;margin:0;padding:9px 18px" onclick="closeModal()">Hủy</button>
      <button id="fwdSubmitBtn" class="btn-primary" style="width:auto;margin:0;padding:9px 20px;opacity:.5" disabled onclick="submitForward()">Chia sẻ</button>
    </div>`);
  renderForwardList();
}

function renderForwardList() {
  const q = (document.getElementById('fwdSearch')?.value || '').trim().toLowerCase();
  ['All','Grp','Fr'].forEach(k => document.getElementById('fwdTab'+k)?.classList.remove('active'));
  document.getElementById(window._fwdTab==='group'?'fwdTabGrp':(window._fwdTab==='friend'?'fwdTabFr':'fwdTabAll'))?.classList.add('active');
  const targets = [
    { id: 'my_cloud', name: 'Cloud của tôi', isGroup: false, kind: 'all' },
    ...groups.map(g => ({ id: g.id, name: g.name, isGroup: true, kind: 'group' })),
    ...contacts.filter(u => u.username !== me).map(u => ({ id: u.username, name: getAlias(u.username) || u.displayName, isGroup: false, kind: 'friend' }))
  ].filter(t => {
    if (window._fwdTab === 'group' && !t.isGroup) return false;
    if (window._fwdTab === 'friend' && t.kind !== 'friend') return false;
    if (q && !t.name.toLowerCase().includes(q)) return false;
    return true;
  });
  document.getElementById('fwdList').innerHTML = targets.map(t => {
    const key = (t.isGroup ? 'g:' : 'u:') + t.id;
    const checked = window._fwdSelected.has(key);
    return `<label class="cv-row" style="cursor:pointer;margin:0;padding:8px 10px">
      <input type="checkbox" class="round-check" ${checked?'checked':''} onchange="toggleFwdTarget('${esc(key)}',this.checked)">
      <div class="avatar" style="width:36px;height:36px;background:${t.id==='my_cloud'?'#0068FF':avColor(t.name)};color:#fff;flex-shrink:0">${t.id==='my_cloud'?'☁️':esc(t.name.slice(0,2).toUpperCase())}</div>
      <span style="font-weight:500;font-size:14.5px">${esc(t.name)}</span>
    </label>`;
  }).join('') || '<div style="text-align:center;color:var(--muted);padding:20px;font-size:13.5px">Không tìm thấy kết quả</div>';
}

function toggleFwdTarget(key, checked) {
  if (checked) window._fwdSelected.add(key); else window._fwdSelected.delete(key);
  const btn = document.getElementById('fwdSubmitBtn');
  if (btn) {
    const cnt = window._fwdSelected.size;
    btn.disabled = cnt === 0;
    btn.style.opacity = cnt === 0 ? '.5' : '1';
    btn.innerText = cnt > 0 ? `Chia sẻ (${cnt})` : 'Chia sẻ';
  }
}

async function submitForward() {
  const note = (document.getElementById('fwdNote')?.value || '').trim();
  const base = window._fwdText || '';
  const content = note ? `${base}\n${note}` : base;
  const ts = new Date().toTimeString().slice(0, 5);
  await Promise.all([...window._fwdSelected].map(key => {
    const isGroup = key.startsWith('g:');
    const targetId = key.slice(2);
    const payload = { type: targetId === 'my_cloud' ? 'my_cloud' : (isGroup ? 'group_message' : 'direct_message'), content, timestamp: ts };
    if (isGroup) payload.groupId = targetId; else if (targetId !== 'my_cloud') payload.toUser = targetId;
    return api('/api/send', 'POST', payload);
  }));
  closeModal();
  await poll();
}

function cancelReply() {
  replyingTo = null;
  const rb = document.getElementById('replyBar');
  if (rb) rb.style.display = 'none';
}

function toggleMsgMore(ev, id) {
  ev.stopPropagation();
  const wrap = ev.currentTarget.closest('.msg-side-actions');
  document.querySelectorAll('.msg-more-menu').forEach(m => { if (m.id !== id) m.style.display = 'none'; });
  document.querySelectorAll('.msg-side-actions.open').forEach(a => { if (a !== wrap) a.classList.remove('open'); });
  const el = document.getElementById(id);
  if (el) {
    const show = el.style.display !== 'block';
    el.style.display = show ? 'block' : 'none';
    if (wrap) wrap.classList.toggle('open', show);
  }
}
function esc(s) { return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
