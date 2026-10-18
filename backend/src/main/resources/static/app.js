/* Threadly — tiny vanilla-JS frontend for the Reddit-clone API (served by Spring Boot). */
const ML_URL = `${location.protocol}//${location.hostname}:8000`;
const MAX_IMAGES = 4;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

const state = {
  view: "feed",       // feed | home | communities | community
  community: null,    // community name when view === "community"
  communityInfo: null,
  sort: "new",
  access: localStorage.getItem("access_token"),
  refresh: localStorage.getItem("refresh_token"),
  email: localStorage.getItem("email"),
  signup: false,
  communities: [],
  joined: new Set(), // ids of communities the user is a member of
  owned: new Set(),  // ids of communities the user created (owners can't leave)
  pending: new Set(), // post ids waiting for moderation result
  images: [],         // [{file, preview}] queued in the composer
};

const $ = (s) => document.querySelector(s);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
/** Only our uploads or plain http(s) links may end up in src/href (the API enforces this too). */
const safeUrl = (u) => (/^(\/media\/|https?:\/\/)/.test(String(u || "")) ? u : "");
const ago = (iso) => {
  const s = (Date.now() - new Date(iso)) / 1000;
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return `${Math.floor(s / 86400)}d ago`;
};
function toast(msg) {
  const t = $("#toast");
  t.textContent = msg;
  t.classList.remove("hidden");
  clearTimeout(toast._t);
  toast._t = setTimeout(() => t.classList.add("hidden"), 3000);
}

// ── API helper with automatic access-token refresh ────────────────────────────
async function api(path, { method = "GET", body, auth = true, retry = true } = {}) {
  const isForm = body instanceof FormData;
  const headers = isForm ? {} : { "Content-Type": "application/json" };
  if (auth && state.access) headers.Authorization = `Bearer ${state.access}`;
  const res = await fetch(path, { method, headers, body: isForm ? body : body ? JSON.stringify(body) : undefined });
  if ((res.status === 401 || res.status === 403) && retry && state.refresh) {
    if (await refreshTokens()) return api(path, { method, body, auth, retry: false });
  }
  const text = await res.text();
  const data = text ? (() => { try { return JSON.parse(text); } catch { return text; } })() : null;
  if (!res.ok) throw new Error((data && data.message) || `${res.status} ${res.statusText}`);
  return data;
}

async function refreshTokens() {
  try {
    const res = await fetch("/api/auth/refresh", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refresh_token: state.refresh }),
    });
    if (!res.ok) throw new Error();
    saveSession(await res.json(), state.email);
    return true;
  } catch {
    logout(false);
    return false;
  }
}

function saveSession(tokens, email) {
  state.access = tokens.access_token;
  state.refresh = tokens.refresh_token;
  state.email = email;
  localStorage.setItem("access_token", state.access);
  localStorage.setItem("refresh_token", state.refresh);
  localStorage.setItem("email", email);
  renderSession();
}

function logout(notify = true) {
  if (state.refresh) fetch("/api/auth/logout", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ refresh_token: state.refresh }) }).catch(() => {});
  state.access = state.refresh = state.email = null;
  ["access_token", "refresh_token", "email"].forEach((k) => localStorage.removeItem(k));
  renderSession();
  if (notify) toast("Logged out");
  loadCommunities().then(() => (state.view === "home" ? go("#/") : render()));
}

function renderSession() {
  document.body.classList.toggle("authed", !!state.access);
  $("#whoami").textContent = state.email ? state.email : "";
}

// ── Auth dialog ──────────────────────────────────────────────────────────────
function setSignup(on) {
  state.signup = on;
  document.body.classList.toggle("signup", on);
  $("#authTitle").textContent = on ? "Create an account" : "Log in";
  $("#authSubmit").textContent = on ? "Sign up" : "Log in";
  $("#authToggle").textContent = on ? "Have an account? Log in" : "Need an account? Sign up";
  $("#authError").textContent = "";
}

async function submitAuth(e) {
  e.preventDefault();
  const email = $("#authEmail").value.trim();
  const password = $("#authPassword").value;
  $("#authError").textContent = "";
  try {
    if (state.signup) {
      const user = await api("/api/users", { method: "POST", auth: false, body: { username: $("#authUsername").value.trim(), email, password } });
      // Dev convenience: backend exposes the verification token when app.auth.expose-verification-token-on-register=true
      if (user.verificationToken) {
        await api(`/api/auth/verify?token=${encodeURIComponent(user.verificationToken)}`, { method: "POST", auth: false });
      }
    }
    const tokens = await api("/api/auth/login", { method: "POST", auth: false, body: { email, password } });
    saveSession(tokens, email);
    $("#authDialog").close();
    toast(state.signup ? "Welcome aboard!" : "Logged in");
    await loadCommunities();
    render();
  } catch (err) {
    $("#authError").textContent = err.message;
  }
}

const requireLogin = () => { setSignup(false); $("#authDialog").showModal(); };

// ── Posts ────────────────────────────────────────────────────────────────────
function mediaGrid(media) {
  const imgs = (media || []).filter((m) => safeUrl(m.mediaUrl));
  if (!imgs.length) return "";
  return `<div class="media-grid n${Math.min(imgs.length, 4)}">${imgs.map((m) => `
    <a href="${esc(safeUrl(m.mediaUrl))}" target="_blank" rel="noopener noreferrer">
      <img src="${esc(safeUrl(m.mediaUrl))}" alt="${esc(m.caption || "post image")}" loading="lazy">
    </a>`).join("")}</div>`;
}

function postCard(p) {
  const pending = state.pending.has(p.id) && !p.moderated;
  const badge = p.moderated
    ? `<span class="badge" title="Masked by the ML moderation pipeline">moderated</span>`
    : pending ? `<span class="badge pending">checking…</span>` : "";
  const community = p.community ? `<a href="#/r/${encodeURIComponent(p.community)}" class="community-link">r/${esc(p.community)}</a> · ` : "";
  return `
  <article class="card post" data-id="${p.id}">
    <div class="votes">
      <button class="${p.userVote === 1 ? "on-up" : ""}" data-vote="1" title="Upvote">▲</button>
      <span class="score">${p.score ?? 0}</span>
      <button class="${p.userVote === -1 ? "on-down" : ""}" data-vote="-1" title="Downvote">▼</button>
    </div>
    <div class="post-body">
      <div class="meta">${community}u/${esc(p.author)} · ${ago(p.createdAt)} ${badge}</div>
      <h4>${esc(p.title)}</h4>
      ${p.text ? `<p>${esc(p.text)}</p>` : ""}
      ${mediaGrid(p.media)}
      <div class="actions">
        <button data-comments>💬 <span class="ccount">${p.commentCount ?? 0}</span> comments</button>
        ${p.canDelete ? `<button data-delete-post class="danger-link">Delete</button>` : ""}
      </div>
      <div class="comments hidden"></div>
    </div>
  </article>`;
}

function feedPath() {
  if (state.view === "home") return `/api/posts/home?sort=${state.sort}`;
  if (state.view === "community") return `/api/posts/community/${encodeURIComponent(state.community)}?sort=${state.sort}`;
  return `/api/posts/global?sort=${state.sort}`;
}

function emptyFeedText() {
  if (state.view === "home") return "Join a community to fill your home feed.";
  if (state.view === "community") return "No posts in this community yet.";
  return "No posts yet — be the first!";
}

async function loadFeed() {
  const feed = $("#feed");
  try {
    const page = await api(feedPath());
    const posts = page.content || [];
    feed.innerHTML = posts.length ? posts.map(postCard).join("") : `<div class="card empty">${emptyFeedText()}</div>`;
    posts.forEach((p) => p.moderated && state.pending.delete(p.id));
  } catch (err) {
    feed.innerHTML = `<div class="card empty">Could not load feed: ${esc(err.message)}</div>`;
  }
}

// Composer images ─────────────────────────────────────────────────────────────
function renderImagePreview() {
  $("#imagePreview").innerHTML = state.images.map((img, i) => `
    <div class="preview"><img src="${img.preview}" alt=""><button type="button" data-remove-image="${i}" title="Remove">✕</button></div>`).join("");
  $("#imgHint").textContent = state.images.length ? `${state.images.length}/${MAX_IMAGES} images` : "PNG, JPEG, GIF or WEBP · up to 5 MB each";
}

function addImages(files) {
  for (const file of files) {
    if (state.images.length >= MAX_IMAGES) { toast(`At most ${MAX_IMAGES} images per post`); break; }
    if (!/^image\/(png|jpeg|gif|webp)$/.test(file.type)) { toast(`${file.name}: only PNG, JPEG, GIF or WEBP`); continue; }
    if (file.size > MAX_IMAGE_BYTES) { toast(`${file.name} is larger than 5 MB`); continue; }
    state.images.push({ file, preview: URL.createObjectURL(file) });
  }
  renderImagePreview();
}

function clearImages() {
  state.images.forEach((img) => URL.revokeObjectURL(img.preview));
  state.images = [];
  $("#postImages").value = "";
  renderImagePreview();
}

async function uploadImages() {
  const media = [];
  for (const img of state.images) {
    const form = new FormData();
    form.append("file", img.file);
    const res = await api("/api/media", { method: "POST", body: form });
    media.push({ mediaUrl: res.url, mediaType: res.mediaType || "IMAGE" });
  }
  return media;
}

async function createPost(e) {
  e.preventDefault();
  const submit = $("#composer button[type=submit]");
  submit.disabled = true;
  try {
    const media = await uploadImages();
    const body = {
      title: $("#postTitle").value.trim(),
      text: $("#postText").value.trim(),
      communityId: $("#postCommunity").value ? Number($("#postCommunity").value) : null,
      media,
    };
    const post = await api("/api/posts", { method: "POST", body });
    $("#postTitle").value = "";
    $("#postText").value = "";
    clearImages();
    state.pending.add(post.id);
    toast("Posted — moderation running in the background");
    if (state.view === "communities") go("#/"); else await loadFeed();
    watchModeration(post.id);
  } catch (err) {
    toast(err.message);
  } finally {
    submit.disabled = false;
  }
}

/** Poll the post a few times so the user sees the ML result land. */
async function watchModeration(id, tries = 0) {
  if (tries > 10) { state.pending.delete(id); return; }
  await new Promise((r) => setTimeout(r, 1500));
  try {
    const p = await api(`/api/posts/${id}`);
    const el = document.querySelector(`.post[data-id="${id}"]`);
    if (p.moderated) {
      state.pending.delete(id);
      if (el) el.outerHTML = postCard(p);
      toast("ML moderation masked part of your post");
      return;
    }
  } catch { /* ignore */ }
  if (tries === 10) {
    state.pending.delete(id);
    document.querySelector(`.post[data-id="${id}"] .badge.pending`)?.remove();
  }
  watchModeration(id, tries + 1);
}

async function vote(article, value) {
  if (!state.access) return requireLogin();
  try {
    const p = await api(`/api/posts/${article.dataset.id}/vote`, { method: "POST", body: { value } });
    // Update in place so open comment threads stay open.
    article.querySelector(".score").textContent = p.score ?? 0;
    const [up, down] = article.querySelectorAll(".votes button");
    up.className = p.userVote === 1 ? "on-up" : "";
    down.className = p.userVote === -1 ? "on-down" : "";
  } catch (err) { toast(err.message); }
}

async function deletePost(article) {
  if (!confirm("Delete this post? This cannot be undone.")) return;
  try {
    await api(`/api/posts/${article.dataset.id}`, { method: "DELETE" });
    article.remove();
    toast("Post deleted");
    if (!document.querySelector("#feed .post")) $("#feed").innerHTML = `<div class="card empty">${emptyFeedText()}</div>`;
  } catch (err) { toast(err.message); }
}

// ── Comments (threaded) ──────────────────────────────────────────────────────
function commentNode(c) {
  const kids = c.children || [];
  const canReply = state.access && !c.isDeleted;
  return `
    <div class="comment" data-cid="${c.id}">
      <div class="meta">
        <button class="collapse" data-collapse title="Collapse thread">[–]</button>
        ${c.isDeleted ? "[deleted]" : `u/${esc(c.author)}`} · ${ago(c.createdAt)} · ${c.score ?? 0} pts
        <span class="collapsed-note">${kids.length ? `(${countReplies(c)} hidden)` : ""}</span>
      </div>
      <div class="comment-body">
        <div class="comment-text">${c.isDeleted ? "<em>[deleted]</em>" : esc(c.text)}</div>
        <div class="actions small">
          ${canReply ? `<button data-reply>Reply</button>` : ""}
          ${c.canDelete ? `<button data-delete-comment class="danger-link">Delete</button>` : ""}
        </div>
        <div class="reply-slot"></div>
        <div class="children">${kids.map(commentNode).join("")}</div>
      </div>
    </div>`;
}

const countReplies = (c) => (c.children || []).reduce((n, k) => n + 1 + countReplies(k), 0);

function commentForm(parentId = "") {
  return `<form class="row comment-form" data-parent="${parentId}">
    <input placeholder="${parentId ? "Write a reply" : "Add a comment"}" required maxlength="10000">
    <button class="primary">${parentId ? "Reply" : "Comment"}</button>
    ${parentId ? `<button type="button" class="ghost" data-cancel-reply>Cancel</button>` : ""}
  </form>`;
}

async function toggleComments(article) {
  const box = article.querySelector(".comments");
  if (!box.classList.contains("hidden")) return box.classList.add("hidden");
  box.classList.remove("hidden");
  await loadComments(article);
}

async function loadComments(article) {
  const box = article.querySelector(".comments");
  box.innerHTML = `<div class="hint">Loading…</div>`;
  try {
    const page = await api(`/api/posts/${article.dataset.id}/comments?sort=old&size=50`);
    const roots = page.content || [];
    box.innerHTML = (state.access ? commentForm() : `<p class="hint">Log in to comment.</p>`) +
      (roots.length ? roots.map(commentNode).join("") : `<p class="hint">No comments yet.</p>`);
  } catch (err) {
    box.innerHTML = `<p class="hint">${esc(err.message)}</p>`;
  }
}

async function submitComment(form) {
  const article = form.closest(".post");
  const input = form.querySelector("input");
  const parentCommentId = form.dataset.parent ? Number(form.dataset.parent) : null;
  form.querySelector("button.primary").disabled = true;
  try {
    await api("/api/comments", { method: "POST", body: { postId: Number(article.dataset.id), parentCommentId, text: input.value } });
    const counter = article.querySelector(".ccount");
    counter.textContent = Number(counter.textContent) + 1;
    await loadComments(article);
  } catch (err) {
    toast(err.message);
    form.querySelector("button.primary").disabled = false;
  }
}

async function deleteComment(commentEl) {
  if (!confirm("Delete this comment?")) return;
  const article = commentEl.closest(".post");
  try {
    await api(`/api/comments/${commentEl.dataset.cid}`, { method: "DELETE" });
    const counter = article.querySelector(".ccount");
    counter.textContent = Math.max(0, Number(counter.textContent) - 1);
    await loadComments(article);
  } catch (err) { toast(err.message); }
}

// ── Communities ──────────────────────────────────────────────────────────────
async function loadCommunities() {
  const [all, joined, owned] = await Promise.all([
    api("/api/communities").catch(() => []),
    state.access ? api("/api/users/me/communities/joined").catch(() => []) : [],
    state.access ? api("/api/users/me/communities/created").catch(() => []) : [],
  ]);
  state.communities = all;
  state.joined = new Set(joined.map((c) => c.id));
  state.owned = new Set(owned.map((c) => c.id));
  renderComposerCommunities();
}

/** Posting into a community requires membership, so only offer joined ones. */
function renderComposerCommunities() {
  const sel = $("#postCommunity");
  const current = sel.value;
  const mine = state.communities.filter((c) => state.joined.has(c.id));
  sel.innerHTML = `<option value="">No community (profile post)</option>` +
    mine.map((c) => `<option value="${c.id}">r/${esc(c.name)}</option>`).join("");
  if (mine.some((c) => String(c.id) === current)) sel.value = current;
}

function membershipActions(c, { allowDelete = false } = {}) {
  if (!state.access) return `<button class="primary" data-login>Log in to join</button>`;
  if (state.owned.has(c.id)) {
    return `<span class="badge member">Owner</span>` +
      (allowDelete ? `<button class="ghost danger" data-delete-community="${c.id}" data-name="${esc(c.name)}">Delete community</button>` : "");
  }
  if (state.joined.has(c.id)) {
    return `<span class="badge member">Joined</span><button class="ghost danger" data-leave="${c.id}" data-name="${esc(c.name)}">Leave</button>`;
  }
  return `<button class="primary" data-join="${c.id}">Join</button>`;
}

const members = (c) => `${c.memberCount ?? 0} member${c.memberCount === 1 ? "" : "s"}`;

function communityCard(c) {
  return `
    <div class="card community">
      <div><a class="community-name" href="#/r/${encodeURIComponent(c.name)}">r/${esc(c.name)}</a>
        <div class="hint">${esc(c.description || "")} · ${members(c)}</div></div>
      <div class="community-actions">${membershipActions(c)}</div>
    </div>`;
}

function renderCommunities() {
  const list = $("#communityList");
  if (!state.communities.length) { list.innerHTML = `<div class="card empty">No communities yet.</div>`; return; }
  if (!state.access) { list.innerHTML = state.communities.map(communityCard).join(""); return; }
  const mine = state.communities.filter((c) => state.joined.has(c.id) || state.owned.has(c.id));
  const others = state.communities.filter((c) => !state.joined.has(c.id) && !state.owned.has(c.id));
  list.innerHTML =
    `<h3 class="section-title">Your communities (${mine.length})</h3>` +
    (mine.length ? mine.map(communityCard).join("") : `<div class="card empty">You haven't joined any communities yet.</div>`) +
    `<h3 class="section-title">Discover (${others.length})</h3>` +
    (others.length ? others.map(communityCard).join("") : `<div class="card empty">You've joined every community.</div>`);
}

async function loadCommunityHeader() {
  const box = $("#communityHeader");
  try {
    const c = await api(`/api/communities/${encodeURIComponent(state.community)}`);
    state.communityInfo = c;
    box.innerHTML = `
      <div class="card community-header">
        <div>
          <h2>r/${esc(c.name)}</h2>
          ${c.description ? `<p>${esc(c.description)}</p>` : ""}
          <p class="hint">Created by u/${esc(c.createdBy)} · ${members(c)} · since ${new Date(c.createdAt).toLocaleDateString()}</p>
        </div>
        <div class="community-actions">${membershipActions(c, { allowDelete: true })}</div>
      </div>`;
    // Preselect this community in the composer when the user can post here.
    if (state.joined.has(c.id)) $("#postCommunity").value = String(c.id);
    return true;
  } catch (err) {
    state.communityInfo = null;
    box.innerHTML = `<div class="card empty">r/${esc(state.community)} — ${esc(err.message)}</div>`;
    return false;
  }
}

async function setMembership(button, id, join) {
  button.disabled = true; // no double join/leave while the request is in flight
  try {
    await api(`/api/communities/${id}/${join ? "join" : "leave"}`, { method: "POST" });
    toast(join ? "Joined!" : "Left community");
    await loadCommunities();
    render();
  } catch (err) {
    toast(err.message);
    button.disabled = false;
  }
}

async function deleteCommunity(button) {
  const name = button.dataset.name;
  if (!confirm(`Delete r/${name}? All of its posts will be removed. This cannot be undone.`)) return;
  button.disabled = true;
  try {
    await api(`/api/communities/${button.dataset.deleteCommunity}`, { method: "DELETE" });
    toast(`r/${name} deleted`);
    await loadCommunities();
    go("#/communities");
  } catch (err) {
    toast(err.message);
    button.disabled = false;
  }
}

async function createCommunity(e) {
  e.preventDefault();
  try {
    const c = await api("/api/communities", { method: "POST", body: { name: $("#communityName").value.trim(), description: $("#communityDesc").value.trim(), isPrivate: false } });
    $("#communityForm").reset();
    toast("Community created");
    await loadCommunities();
    go(`#/r/${encodeURIComponent(c.name)}`);
  } catch (err) { toast(err.message); }
}

/** Join / leave / delete buttons appear in the list and on the community page. */
function onCommunityAction(e) {
  if (e.target.closest("[data-login]")) return requireLogin();
  const join = e.target.closest("[data-join]");
  if (join) return setMembership(join, join.dataset.join, true);
  const leave = e.target.closest("[data-leave]");
  if (leave) return confirm(`Leave r/${leave.dataset.name}?`) && setMembership(leave, leave.dataset.leave, false);
  const del = e.target.closest("[data-delete-community]");
  if (del) return deleteCommunity(del);
}

// ── Status panel ─────────────────────────────────────────────────────────────
async function loadStatus() {
  const set = (el, ok) => { el.textContent = ok ? "UP" : "DOWN"; el.className = `pill ${ok ? "up" : "down"}`; };
  try { const r = await fetch("/api/health"); set($("#stApi"), r.ok); } catch { set($("#stApi"), false); }
  try {
    const r = await fetch(`${ML_URL}/stats`);
    const s = await r.json();
    set($("#stMl"), r.ok);
    $("#mlInfo").textContent = `Moderator: ${s.llmEnabled ? `Groq LLM (${s.llmModel}) + profanity filter` : "profanity filter (add GROQ_API_KEY for LLM)"} · Kafka ${s.connected ? "connected" : "connecting"} · ${s.consumed} posts processed, ${s.moderated} masked`;
  } catch { set($("#stMl"), false); $("#mlInfo").textContent = ""; }
}

// ── Routing (hash based, so community pages have their own URL) ──────────────
//   #/  global feed · #/home · #/communities · #/r/<name>
function go(hash) {
  if (location.hash === hash) route(); else location.hash = hash;
}

function route() {
  const h = location.hash.replace(/^#/, "") || "/";
  const m = h.match(/^\/r\/([^/]+)$/);
  if (m) { state.view = "community"; state.community = decodeURIComponent(m[1]); }
  else if (h === "/home" && state.access) state.view = "home";
  else if (h === "/communities") state.view = "communities";
  else state.view = "feed";
  render();
}

function render() {
  const v = state.view;
  const tab = v === "community" ? "communities" : v;
  document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.dataset.view === tab));
  const isList = v === "communities";
  $("#communitiesView").classList.toggle("hidden", !isList);
  $("#communityHeader").classList.toggle("hidden", v !== "community");
  $("#feed").classList.toggle("hidden", isList);
  $(".feed-head").classList.toggle("hidden", isList);
  $("#composer").classList.toggle("hidden", isList);
  $("#feedTitle").textContent = v === "home" ? "Home feed" : v === "community" ? "Posts" : "Global feed";
  if (isList) return renderCommunities();
  if (v === "community") {
    loadCommunityHeader().then((ok) => (ok ? loadFeed() : ($("#feed").innerHTML = "")));
  } else {
    loadFeed();
  }
}

// ── Wire up ──────────────────────────────────────────────────────────────────
const VIEW_HASH = { feed: "#/", home: "#/home", communities: "#/communities" };
document.querySelectorAll(".tab").forEach((t) => t.addEventListener("click", () => go(VIEW_HASH[t.dataset.view])));
window.addEventListener("hashchange", route);
$("#sortSel").addEventListener("change", (e) => { state.sort = e.target.value; loadFeed(); });
$("#loginOpen").addEventListener("click", requireLogin);
$("#authClose").addEventListener("click", () => $("#authDialog").close());
$("#authToggle").addEventListener("click", () => setSignup(!state.signup));
$("#authForm").addEventListener("submit", submitAuth);
$("#logoutBtn").addEventListener("click", () => logout());
$("#composer").addEventListener("submit", createPost);
$("#postImages").addEventListener("change", (e) => addImages(e.target.files));
$("#imagePreview").addEventListener("click", (e) => {
  const b = e.target.closest("[data-remove-image]");
  if (!b) return;
  const [img] = state.images.splice(Number(b.dataset.removeImage), 1);
  URL.revokeObjectURL(img.preview);
  renderImagePreview();
});
$("#communityForm").addEventListener("submit", createCommunity);
$("#communityList").addEventListener("click", onCommunityAction);
$("#communityHeader").addEventListener("click", onCommunityAction);

$("#feed").addEventListener("click", (e) => {
  const article = e.target.closest(".post");
  if (!article) return;
  const v = e.target.closest("[data-vote]");
  if (v) return vote(article, Number(v.dataset.vote));
  if (e.target.closest("[data-comments]")) return toggleComments(article);
  if (e.target.closest("[data-delete-post]")) return deletePost(article);

  const comment = e.target.closest(".comment");
  if (!comment) return;
  if (e.target.closest("[data-collapse]")) {
    const collapsed = comment.classList.toggle("collapsed");
    e.target.closest("[data-collapse]").textContent = collapsed ? "[+]" : "[–]";
    return;
  }
  if (e.target.closest("[data-reply]")) {
    const slot = comment.querySelector(":scope > .comment-body > .reply-slot");
    if (!slot.innerHTML) { slot.innerHTML = commentForm(comment.dataset.cid); slot.querySelector("input").focus(); }
    return;
  }
  if (e.target.closest("[data-cancel-reply]")) {
    comment.querySelector(":scope > .comment-body > .reply-slot").innerHTML = "";
    return;
  }
  if (e.target.closest("[data-delete-comment]")) return deleteComment(comment);
});
$("#feed").addEventListener("submit", (e) => {
  const form = e.target.closest(".comment-form");
  if (!form) return;
  e.preventDefault();
  submitComment(form);
});

renderSession();
renderImagePreview();
loadCommunities().then(route);
loadStatus();
setInterval(loadStatus, 10000);
