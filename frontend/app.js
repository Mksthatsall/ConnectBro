/* ConnectBro browser client. Uses the Spring API Gateway; it never fabricates user or post data. */
const API_HOST = ['localhost', '127.0.0.1'].includes(location.hostname) ? location.hostname : 'localhost';
const API_ROOT = window.CONNECTBRO_API_ROOT || `http://${API_HOST}:8090/api/v1`;
// Older builds persisted an API URL in localStorage. Ignore it so a stale port
// cannot keep the browser pointed at an API gateway that is no longer running.
localStorage.removeItem('connectbro_api');
const app = document.querySelector('#app');
const state = { token: localStorage.getItem('connectbro_token'), me: null, view: 'feed', networkTab: 'recommendations',
  network: null, feed: null, notifications: null, searchTerm: '', searchResults: null, publicProfile: null, unread: 0, recommendPage: 0, recommendationGoal: '' };

const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const safeImage = value => /^https?:\/\//i.test(value || '') ? esc(value) : '';
const initials = name => String(name || 'ConnectBro').split(/\s+/).slice(0,2).map(x => x[0] || '').join('').toUpperCase();
const avatar = (profile, size = '') => {
  const picture = safeImage(profile?.profilePicture);
  return picture
    ? `<img class="avatar avatar-image ${size}" src="${picture}" data-initials="${esc(initials(profile?.name))}" alt="">`
    : `<span class="avatar ${size}">${esc(initials(profile?.name))}</span>`;
};
const when = value => value ? new Intl.DateTimeFormat(undefined,{dateStyle:'medium',timeStyle:'short'}).format(new Date(value)) : 'Just now';
const splitValues = value => String(value || '').split(',').map(x => x.trim()).filter(Boolean);
const skillPills = value => splitValues(value).slice(0,5).map(x => `<span class="pill">${esc(x)}</span>`).join('');

function tokenUserId() {
  try { return JSON.parse(atob(state.token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/'))).sub; }
  catch { return null; }
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (state.token) headers.set('Authorization', `Bearer ${state.token}`);
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type','application/json');
  let response;
  try {
    response = await fetch(`${API_ROOT}${path}`, {...options, headers});
  } catch {
    throw new Error(`Cannot reach the API gateway at ${API_ROOT}. Start ApiGateway on port 8090, then reload this page.`);
  }
  const raw = await response.text();
  let body = null;
  if (raw) { try { body = JSON.parse(raw); } catch { body = raw; } }
  if (response.status === 401 && state.token) {
    logout(false);
    throw new Error('Your session expired. Please sign in again.');
  }
  if (!response.ok) {
    const message = body?.message || body?.error || body || `Request failed (${response.status})`;
    throw new Error(typeof message === 'string' ? message : JSON.stringify(message));
  }
  return body;
}

function toast(message, error = false) {
  document.querySelector('.toast')?.remove();
  const node = document.createElement('div'); node.className = `toast${error ? ' error' : ''}`; node.textContent = message;
  document.body.append(node); setTimeout(() => node.remove(), 3600);
}

function renderAuth(register = false) {
  app.innerHTML = `<main class="auth-wrap">
    <section class="auth-visual"><div class="brand-lockup"><span class="brand-mark">cb</span> ConnectBro</div>
      <div class="auth-copy"><div class="eyebrow">Grow with your people</div><h1>Your next opportunity starts with a conversation.</h1>
      <p>Build your professional circle, share what you’re working on, and discover people connected to your network.</p></div>
      <div class="network-note"><span>Connect around shared skills and useful conversations.</span></div>
    </section><section class="auth-panel"><form class="auth-card" id="auth-form" data-register="${register}">
      <h2>${register ? 'Create your account' : 'Welcome back'}</h2><p>${register ? 'Start building your professional network.' : 'Sign in to pick up where you left off.'}</p>
      ${register ? `<div class="field"><label for="name">Full name</label><input id="name" name="name" autocomplete="name" required maxlength="120" placeholder="Your name"></div>` : ''}
      <div class="field"><label for="email">Email address</label><input id="email" name="email" type="email" autocomplete="email" required placeholder="you@company.com"></div>
      <div class="field"><label for="password">Password</label><input id="password" name="password" type="password" autocomplete="${register?'new-password':'current-password'}" required minlength="8" placeholder="At least 8 characters"></div>
      <button class="btn btn-primary btn-block" type="submit">${register ? 'Create account' : 'Sign in'} <span aria-hidden="true">→</span></button>
      ${!register ? `<button class="btn btn-secondary btn-block" id="load-demo" type="button" style="margin-top:10px">Load a demo network</button><div id="demo-status" class="composer-hint" style="text-align:center;margin-top:9px;min-height:16px"></div>` : ''}
      <div class="auth-toggle">${register ? 'Already a member?' : 'New to ConnectBro?'} <button class="text-button" id="auth-toggle" type="button">${register ? 'Sign in' : 'Create an account'}</button></div>
    </form></section></main>`;
  document.querySelector('#auth-toggle').onclick = () => renderAuth(!register);
  document.querySelector('#load-demo')?.addEventListener('click', async event => {
    const button = event.currentTarget; const status = document.querySelector('#demo-status');
    button.disabled = true; button.textContent = 'Preparing sample network…';
    try {
      const token = await window.ConnectBroDemo.run(API_ROOT, message => { if (status) status.textContent = message; });
      state.token = token; localStorage.setItem('connectbro_token', token); state.view = 'feed';
      await initialize(); toast('Demo network is ready. Sign in later with the demo account shown in the README.');
    } catch (error) { toast(error.message,true); button.disabled = false; button.textContent = 'Retry demo setup'; if (status) status.textContent = 'Setup did not finish. You can retry after checking the services.'; }
  });
  document.querySelector('#auth-form').onsubmit = async event => {
    event.preventDefault(); const form = new FormData(event.currentTarget); const submit = event.currentTarget.querySelector('button[type=submit]'); submit.disabled = true; submit.textContent = 'Please wait…';
    try {
      if (register) await api('/users/auth/signup',{method:'POST',body:JSON.stringify({name:form.get('name'),email:form.get('email'),password:form.get('password')})});
      const token = await api('/users/auth/login',{method:'POST',body:JSON.stringify({email:form.get('email'),password:form.get('password')})});
      state.token = String(token).replace(/^"|"$/g,''); localStorage.setItem('connectbro_token',state.token);
      await initialize(); toast(register ? 'Your account is ready.' : 'Welcome back.');
    } catch (error) { toast(error.message,true); submit.disabled = false; submit.textContent = register ? 'Create account →' : 'Sign in →'; }
  };
}

function logout(show = true) {
  localStorage.removeItem('connectbro_token'); state.token = null; state.me = null; state.view = 'feed';
  renderAuth(); if (show) toast('You have signed out.');
}

function icon(name) {
  const paths = {home:'<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
    network:'<circle cx="9" cy="8" r="3"/><circle cx="17" cy="9" r="2.5"/><path d="M3 20a6 6 0 0 1 12 0M15 16a5 5 0 0 1 6 4"/>',
    search:'<circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 5 5"/>',profile:'<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    bell:'<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>'};
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name]}</svg>`;
}

function renderShell() {
  const nav = [['feed','Home','home'],['network','My Network','network'],['search','Find people','search'],['profile','My profile','profile'],['notifications','Notifications','bell']];
  app.innerHTML = `<div class="app-shell"><header class="topbar">
    <a class="top-brand" href="#" data-view="feed"><span class="brand-mark">cb</span> ConnectBro</a>
    <form class="top-search" id="global-search"><span class="search-icon">⌕</span><input name="q" aria-label="Search people" placeholder="Search people, skills, interests…"></form>
    <div class="top-actions"><button class="icon-button" data-view="notifications" aria-label="Notifications">${icon('bell')}<span id="unread-badge" class="badge" ${state.unread ? '' : 'hidden'}>${state.unread>9?'9+':state.unread}</span></button>
      <div class="user-chip">${avatar(state.me,'avatar-sm')}<span>${esc(state.me?.name || 'My account')}</span></div><button class="btn btn-quiet btn-sm" id="logout">Sign out</button></div>
  </header><div class="workspace"><aside class="sidebar"><div class="side-profile"><div class="side-profile-row">${avatar(state.me)}<div><div class="side-profile-name">${esc(state.me?.name || 'Your profile')}</div><div class="side-profile-head">${esc(state.me?.headline || 'Add a professional headline')}</div></div></div></div>
    <nav class="side-nav">${nav.map(([key,label,ico])=>`<button class="nav-item ${state.view===key?'active':''}" data-view="${key}">${icon(ico)}<span>${label}</span>${key==='notifications'&&state.unread?`<span class="badge" style="position:static;margin-left:auto">${state.unread}</span>`:''}</button>`).join('')}</nav>
    <div class="side-foot">A professional network built around useful connections.</div></aside>
    <main class="main-column" id="main-view"></main>
    <aside class="right-column"><section class="right-card"><h3>Build your circle</h3><p>Strong networks grow through shared work, useful conversations and a little curiosity.</p><button class="btn btn-secondary btn-sm" style="margin-top:14px" data-view="network">Explore recommendations →</button></section>
      <section class="right-card"><h3>Your profile</h3><p>Profiles with a clear headline and skills are easier for the right people to find.</p><button class="btn btn-quiet btn-sm" style="margin-top:10px" data-view="profile">Update profile →</button></section></aside>
  </div></div>`;
  document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', event => { event.preventDefault(); navigate(button.dataset.view); }));
  document.querySelector('#logout').onclick = () => logout();
  document.querySelector('#global-search').onsubmit = event => { event.preventDefault(); state.searchTerm = new FormData(event.currentTarget).get('q').trim(); navigate('search'); };
  renderView();
}

async function initialize() {
  try { state.me = await api('/users/me'); }
  catch (error) { toast(error.message,true); if (state.token) logout(false); return; }
  renderShell(); refreshUnread();
}

function navigate(view) {
  state.view = view;
  if (view === 'network') state.networkTab = state.networkTab || 'recommendations';
  renderShell();
}

function heading(title, subtitle, right = '') { return `<div class="page-heading"><div><h1>${title}</h1><p>${subtitle}</p></div>${right}</div>`; }
function empty(title, detail, symbol = '✳') { return `<div class="empty-state"><div class="empty-icon">${symbol}</div><strong>${esc(title)}</strong><span>${esc(detail)}</span></div>`; }

async function renderView() {
  const host = document.querySelector('#main-view'); if (!host) return;
  host.innerHTML = '<div class="card"><div class="loading-line"></div></div>';
  try {
    if (state.view === 'feed') await renderFeed(host);
    else if (state.view === 'network') await renderNetwork(host);
    else if (state.view === 'search') await renderSearch(host);
    else if (state.view === 'profile') await renderProfile(host);
    else if (state.view === 'notifications') await renderNotifications(host);
    else if (state.view === 'publicProfile') await renderPublicProfile(host);
  } catch (error) { host.innerHTML = `<section class="card">${empty('Could not load this page',error.message,'!')}<button class="btn btn-outline btn-sm" id="retry">Try again</button></section>`; document.querySelector('#retry').onclick=renderView; }
}

async function profileFor(userId) {
  if (Number(userId) === Number(state.me?.id)) return state.me;
  try { return await api(`/users/profiles/${encodeURIComponent(userId)}`); }
  catch { return {id:userId,name:`Professional ${userId}`}; }
}

async function hydratePosts(posts) {
  return Promise.all(posts.map(async post => {
    const [profile, engagement, comments] = await Promise.all([
      profileFor(post.userId), api(`/posts/likes/${post.id}`).catch(()=>({likeCount:0,likedByMe:false})),
      api(`/posts/core/${post.id}/comments`).catch(()=>[])
    ]);
    const hydratedComments = await Promise.all((comments||[]).map(async comment=>({...comment,profile:await profileFor(comment.userId)})));
    return {...post,profile,engagement,comments:hydratedComments};
  }));
}

function renderPost(post) {
  const comments = post.comments || [];
  return `<article class="card post-card" data-post-id="${post.id}"><div class="post-head">${avatar(post.profile)}<div><div class="post-author">${esc(post.profile?.name)}</div><div class="post-headline">${esc(post.profile?.headline || 'ConnectBro member')}</div></div><time class="post-time">${esc(when(post.createdAt))}</time></div>
    <div class="post-content">${esc(post.content)}</div><div class="post-stats"><span>${post.engagement?.likeCount || 0} ${post.engagement?.likeCount===1?'like':'likes'}</span><span>${comments.length} ${comments.length===1?'comment':'comments'}</span></div>
    <div class="post-actions"><button class="post-action ${post.engagement?.likedByMe?'is-liked':''}" data-action="like" data-id="${post.id}">${post.engagement?.likedByMe?'♥ Liked':'♡ Like'}</button><button class="post-action" data-action="focus-comment" data-id="${post.id}">▱ Comment</button></div>
    <div class="comments">${comments.map(comment=>`<div class="comment-row">${avatar(comment.profile || {name:`Member ${comment.userId}`},'avatar-sm')}<div class="comment-bubble"><strong>${esc(comment.profile?.name || `Member ${comment.userId}`)}</strong>${esc(comment.content)}</div></div>`).join('')}
      <form class="comment-form" data-comment-form="${post.id}">${avatar(state.me,'avatar-sm')}<input name="content" maxlength="3000" required placeholder="Write a comment…"><button class="btn btn-secondary btn-sm">Send</button></form></div></article>`;
}

async function renderFeed(host) {
  const posts = await api('/posts/core/feed');
  const hydrated = await hydratePosts(posts || []);
  host.innerHTML = `${heading('Your feed','Ideas and updates from your professional circle.')}
    <form class="card" id="post-composer"><div class="composer-top">${avatar(state.me)}<textarea class="composer-area" name="content" maxlength="5000" required placeholder="Share an idea, a lesson, or what you’re working on…"></textarea></div><div class="composer-footer"><span class="composer-hint">Good conversations start with a useful thought.</span><button class="btn btn-primary btn-sm">Share post</button></div></form>
    ${hydrated.length ? hydrated.map(renderPost).join('') : `<section class="card">${empty('Your feed is ready for a first post','Share an update or connect with people to bring your network into the feed.','✍')}</section>`}`;
  document.querySelector('#post-composer').onsubmit = async event => {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    try { await api('/posts/core',{method:'POST',body:JSON.stringify({content:form.get('content')})}); toast('Your post is live.'); await renderView(); }
    catch(error){toast(error.message,true);}
  };
  host.querySelectorAll('[data-action="like"]').forEach(button => button.onclick = async () => {
    try { await api(`/posts/likes/${button.dataset.id}`,{method:button.classList.contains('is-liked')?'DELETE':'POST'}); await renderView(); }
    catch(error){toast(error.message,true);}
  });
  host.querySelectorAll('[data-action="focus-comment"]').forEach(button => button.onclick = () => host.querySelector(`[data-comment-form="${button.dataset.id}"] input`)?.focus());
  host.querySelectorAll('[data-comment-form]').forEach(form => form.onsubmit = async event => {
    event.preventDefault(); const content = new FormData(form).get('content');
    try { await api(`/posts/core/${form.dataset.commentForm}/comments`,{method:'POST',body:JSON.stringify({content})}); await renderView(); }
    catch(error){toast(error.message,true);}
  });
}

function personRow(profile, subtitle, actions = '') {
  return `<div class="list-row">${avatar(profile)}<div class="list-main"><div class="list-name">${esc(profile?.name || `Professional ${profile?.id}`)}</div><div class="list-sub">${esc(profile?.headline || subtitle || 'ConnectBro member')}${profile?.location?` · ${esc(profile.location)}`:''}</div>${profile?.skills?`<div class="skill-pills">${skillPills(profile.skills)}</div>`:''}</div><div class="list-actions">${actions}</div></div>`;
}

async function renderNetwork(host) {
  const tabs = [['recommendations','Recommendations'],['connections','Connections'],['incoming','Received'],['outgoing','Sent']];
  host.innerHTML = `${heading('My network','Build meaningful professional connections.')}
    <section class="card"><div class="tabs">${tabs.map(([key,label])=>`<button class="tab ${state.networkTab===key?'active':''}" data-tab="${key}">${label}</button>`).join('')}</div><div id="network-list"><div class="loading-line"></div></div></section>`;
  host.querySelectorAll('[data-tab]').forEach(tab=>tab.onclick=()=>{state.networkTab=tab.dataset.tab;renderView();});
  const target = host.querySelector('#network-list');
  if (state.networkTab==='recommendations') {
    const goalParam = state.recommendationGoal ? `&goal=${encodeURIComponent(state.recommendationGoal)}` : '';
    const page = await api(`/connections/core/recommendations?page=${state.recommendPage}&size=20${goalParam}`);
    const people = page?.items || [];
    target.innerHTML = `${people.length ? people.map(item=>`<div class="list-row">${avatar(item)}<div class="list-main"><div class="list-name">${esc(item.name||`Professional ${item.userId}`)}</div><div class="list-sub">${esc(item.headline||'ConnectBro member')} · ${item.degree===2?'2nd':'3rd'} degree</div>${item.sharedSkills?.length?`<div class="skill-pills">${item.sharedSkills.map(skill=>`<span class="pill">Skill · ${esc(skill)}</span>`).join('')}</div>`:''}${item.sharedInterests?.length?`<div class="skill-pills">${item.sharedInterests.map(value=>`<span class="pill">Interest · ${esc(value)}</span>`).join('')}</div>`:''}${item.sharedGoals?.length?`<div class="skill-pills">${item.sharedGoals.map(value=>`<span class="pill">Goal · ${esc(value)}</span>`).join('')}</div>`:''}<div class="reason">${(item.reasons||[]).map(esc).join(' ')}</div><div class="reason">Skill fit ${item.skillSimilarity}% · Interest fit ${item.interestSimilarity}% · Goal alignment ${item.goalAlignment}%</div></div><div class="list-actions"><span class="pill">${item.score} match</span><button class="btn btn-outline btn-sm" data-connect="${item.userId}">Connect</button></div></div>`).join('') : empty('No recommendations yet','Add connections and complete your skills, interests, and goals to improve suggestions.','◎')}${page?.total>20?`<div class="composer-footer"><button class="btn btn-outline btn-sm" id="recommend-prev" ${state.recommendPage===0?'disabled':''}>Previous</button><span class="composer-hint">Page ${page.page+1} of ${Math.ceil(page.total/page.size)}</span><button class="btn btn-outline btn-sm" id="recommend-next" ${(page.page+1)*page.size>=page.total?'disabled':''}>Next</button></div>`:''}`;
    target.insertAdjacentHTML('afterbegin', `<form id="recommend-goal-form" style="display:flex;gap:10px;margin:16px 0"><input name="goal" maxlength="200" value="${esc(state.recommendationGoal)}" placeholder="Optional: what are you looking for? e.g. AI mentor or startup cofounder" style="flex:1;min-width:0"><button class="btn btn-primary btn-sm">Find matches</button></form>`);
    target.querySelector('#recommend-goal-form')?.addEventListener('submit',event=>{event.preventDefault();state.recommendationGoal=new FormData(event.currentTarget).get('goal').trim();state.recommendPage=0;renderView();});
    target.querySelector('#recommend-prev')?.addEventListener('click',()=>{state.recommendPage--;renderView();});
    target.querySelector('#recommend-next')?.addEventListener('click',()=>{state.recommendPage++;renderView();});
    target.querySelectorAll('[data-connect]').forEach(button=>button.onclick=async()=>{
      button.disabled=true;try{await api(`/connections/core/requests/${button.dataset.connect}`,{method:'POST'});toast('Connection request sent.');await renderView();}
      catch(error){button.disabled=false;toast(error.message,true);}
    });
  } else if (state.networkTab==='connections') {
    const rows = await api('/connections/core/connections');
    const ids = (rows||[]).map(row=>Number(row.lowUserId)===Number(state.me.id)?row.highUserId:row.lowUserId);
    const people = await Promise.all(ids.map(profileFor));
    target.innerHTML = people.length ? people.map(profile=>personRow(profile,'Connected',`<button class="btn btn-outline btn-sm" data-remove="${profile.id}">Remove</button>`)).join('') : empty('Your circle is just beginning','Accepted connections will show up here.','◎');
    target.querySelectorAll('[data-remove]').forEach(button=>button.onclick=async()=>{if(!confirm('Remove this connection?'))return;try{await api(`/connections/core/connections/${button.dataset.remove}`,{method:'DELETE'});await renderView();}catch(error){toast(error.message,true);}});
  } else {
    const incoming = state.networkTab==='incoming';
    const rows = await api(`/connections/core/requests/${incoming?'incoming':'outgoing'}`);
    const people = await Promise.all((rows||[]).map(async row=>({...row,profile:await profileFor(incoming?row.requesterUserId:row.receiverUserId)})));
    target.innerHTML = people.length ? people.map(row=>personRow(row.profile,incoming?'Wants to connect with you':'Request awaiting a response',incoming?
      `<button class="btn btn-primary btn-sm" data-accept="${row.id}">Accept</button><button class="btn btn-outline btn-sm" data-reject="${row.id}">Decline</button>`:
      `<button class="btn btn-outline btn-sm" data-cancel="${row.id}">Cancel</button>`)).join('') : empty(incoming?'No new requests':'No sent requests',incoming?'Connection requests you receive will appear here.':'Requests you send will appear here.','↗');
    target.querySelectorAll('[data-accept]').forEach(button=>button.onclick=()=>requestAction(button,'accept'));
    target.querySelectorAll('[data-reject]').forEach(button=>button.onclick=()=>requestAction(button,'reject'));
    target.querySelectorAll('[data-cancel]').forEach(button=>button.onclick=()=>requestAction(button,'cancel'));
  }
}

async function requestAction(button, action) {
  try { const path = action==='cancel'?`/connections/core/requests/${button.dataset[action]}`:`/connections/core/requests/${button.dataset[action]}/${action}`;
    await api(path,{method:action==='cancel'?'DELETE':'POST'});toast(action==='accept'?'Connection accepted.':action==='reject'?'Request declined.':'Request cancelled.');await renderView(); }
  catch(error){toast(error.message,true);}
}

async function renderSearch(host) {
  host.innerHTML = `${heading('Find people','Search names, skills, interests and current goals.')}
    <form class="card" id="search-form"><div class="field" style="margin:0"><label for="people-query">What are you looking for?</label><input class="search-input" id="people-query" name="q" value="${esc(state.searchTerm)}" placeholder="Try “product design” or “Java”"></div><button class="btn btn-primary btn-sm" style="margin-top:12px">Search network</button></form>
    <section class="card" id="search-results">${state.searchResults===null?empty('Search your professional community','Try a person’s name, a skill, or a role.','⌕'):''}</section>`;
  host.querySelector('#search-form').onsubmit=async event=>{event.preventDefault();state.searchTerm=new FormData(event.currentTarget).get('q').trim();try{state.searchResults=await api(`/users/search?q=${encodeURIComponent(state.searchTerm)}`);await renderView();}catch(error){toast(error.message,true);}};
  if(state.searchResults){const results=host.querySelector('#search-results');
    results.innerHTML=state.searchResults.length?state.searchResults.filter(user=>Number(user.id)!==Number(state.me.id)).map(user=>personRow(user,'',`<button class="btn btn-quiet btn-sm" data-profile="${user.id}">Profile</button><button class="btn btn-outline btn-sm" data-connect="${user.id}">Connect</button>`)).join(''):empty('No people found','Try a broader name, skill or role.','⌕');
    results.querySelectorAll('[data-profile]').forEach(button=>button.onclick=()=>openProfile(button.dataset.profile));
    results.querySelectorAll('[data-connect]').forEach(button=>button.onclick=async()=>{try{await api(`/connections/core/requests/${button.dataset.connect}`,{method:'POST'});toast('Connection request sent.');button.textContent='Sent';button.disabled=true;}catch(error){toast(error.message,true);}});
  }
}

async function openProfile(userId) {
  try { state.publicProfile=await api(`/users/profiles/${userId}`); state.view='publicProfile'; renderShell(); }
  catch(error){toast(error.message,true);}
}

async function renderProfile(host) {
  const profile=await api('/users/me');state.me=profile;
  host.innerHTML=`${heading('My profile','Share the experience and skills you bring to your network.')}
    <section class="card"><div class="profile-cover"></div><div class="profile-top">${avatar(profile)}<div class="profile-info"><h2>${esc(profile.name)}</h2><p>${esc(profile.headline||'Add your headline below')}</p></div></div>
      <p class="profile-about">${esc(profile.about||'Add a short introduction about your work and interests.')}</p>
      <form id="profile-form"><div class="profile-fields">
        ${field('Professional headline','headline',profile.headline,'e.g. Product designer at Acme')}
        ${field('Location','location',profile.location,'City, country')}
        ${field('Profile photo URL','profilePicture',profile.profilePicture,'https://…')}
        ${field('Skills (comma separated)','skills',profile.skills,'Research, Product strategy, Figma')}
        ${field('Interests (comma separated)','interests',profile.interests,'Accessibility, Climate tech')}
        ${field('Current goals (comma separated)','goals',profile.goals,'Finding a mentor, Building an AI product')}
        <div class="field wide"><label for="about">About</label><textarea id="about" name="about" maxlength="2000" placeholder="A few lines about your work…">${esc(profile.about||'')}</textarea></div>
      </div><button class="btn btn-primary">Save profile</button></form></section>`;
  host.querySelector('#profile-form').onsubmit=async event=>{event.preventDefault();const data=Object.fromEntries(new FormData(event.currentTarget));try{state.me=await api('/users/me',{method:'PUT',body:JSON.stringify(data)});toast('Profile saved.');renderShell();}catch(error){toast(error.message,true);}};
}
function field(label,name,value,placeholder){return `<div class="field"><label for="${name}">${label}</label><input id="${name}" name="${name}" value="${esc(value||'')}" maxlength="${name==='profilePicture'?1000:500}" placeholder="${esc(placeholder)}"></div>`;}

async function renderPublicProfile(host) {
  const profile=state.publicProfile;
  host.innerHTML=`${heading('Professional profile','A member of your extended community.',`<button class="btn btn-outline btn-sm" data-view="search">← Back to search</button>`)}
    <section class="card"><div class="profile-cover"></div><div class="profile-top">${avatar(profile)}<div class="profile-info"><h2>${esc(profile.name)}</h2><p>${esc(profile.headline||'ConnectBro member')}${profile.location?` · ${esc(profile.location)}`:''}</p></div></div>
      <p class="profile-about">${esc(profile.about||'')}</p><h3 class="section-title">Skills</h3><div class="skill-pills">${skillPills(profile.skills)||'<span class="list-sub">No skills listed yet.</span>'}</div>
      <h3 class="section-title" style="margin-top:20px">Interests</h3><div class="skill-pills">${skillPills(profile.interests)||'<span class="list-sub">No interests listed yet.</span>'}</div>
      <h3 class="section-title" style="margin-top:20px">Current goals</h3><div class="skill-pills">${skillPills(profile.goals)||'<span class="list-sub">No goals listed yet.</span>'}</div>
      <button class="btn btn-primary" data-connect="${profile.id}">Connect</button></section>`;
  host.querySelector('[data-connect]').onclick=async()=>{try{await api(`/connections/core/requests/${profile.id}`,{method:'POST'});toast('Connection request sent.');}catch(error){toast(error.message,true);}};
  host.querySelector('[data-view]').onclick=()=>navigate('search');
}

async function renderNotifications(host) {
  const [items,unread]=await Promise.all([api('/notifications/core'),api('/notifications/core/unread-count')]);state.unread=Number(unread)||0;
  (items || []).forEach(item => { item.type = item.type || ''; });
  host.innerHTML=`${heading('Notifications','Stay up to date with your professional circle.',state.unread?'<button class="btn btn-outline btn-sm" id="mark-all">Mark all read</button>':'')}
    <section class="card">${items?.length?items.map(item=>`<div class="notification-row ${item.read?'':'unread'}"><span class="avatar avatar-sm">${item.type==='LIKE'?'♥':item.type==='COMMENT'?'▱':item.type.startsWith('CONNECTION')?'↗':'•'}</span><div class="notification-copy">${esc(item.message)}<time>${esc(when(item.createdAt))}</time></div>${item.read?'':`<button class="btn btn-quiet btn-sm" data-read="${item.id}">Mark read</button>`}</div>`).join(''):empty('You’re all caught up','New connection and post activity will appear here.','♧')}</section>`;
  host.querySelector('#mark-all')?.addEventListener('click',async()=>{try{await api('/notifications/core/read-all',{method:'POST'});await refreshUnread();await renderView();}catch(error){toast(error.message,true);}});
  host.querySelectorAll('[data-read]').forEach(button=>button.onclick=async()=>{try{await api(`/notifications/core/${button.dataset.read}/read`,{method:'POST'});await refreshUnread();await renderView();}catch(error){toast(error.message,true);}});
}

async function refreshUnread() {
  try { state.unread=Number(await api('/notifications/core/unread-count'))||0; const badge=document.querySelector('#unread-badge'); if(badge){badge.hidden=!state.unread;badge.textContent=state.unread>9?'9+':state.unread;} }
  catch { /* service may be starting; the page displays its own error state when opened */ }
}

if (state.token) initialize(); else renderAuth();
setInterval(()=>{if(state.token)refreshUnread();},45000);
document.addEventListener('error', event => {
  const image = event.target;
  if (!image?.classList?.contains('avatar-image')) return;
  const fallback = document.createElement('span');
  fallback.className = image.className.replace('avatar-image','').trim();
  fallback.textContent = image.dataset.initials || 'CB';
  image.replaceWith(fallback);
}, true);
