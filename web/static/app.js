const state = { kind: "posts", page: 1, limit: 50, total: 0 };

const columns = {
  posts: [
    ["id", "ID", "mono"], ["nickname", "닉네임", ""], ["user_key", "식별자", "mono"],
    ["created_at", "작성 시각", "mono"], ["view_count", "조회", "mono"],
    ["recommend_count", "추천", "mono"], ["comment_count", "댓글", "mono"]
  ],
  comments: [
    ["id", "ID", "mono"], ["nickname", "닉네임", ""], ["user_key", "식별자", "mono"],
    ["created_at", "작성 시각", "mono"]
  ]
};

const $ = (selector) => document.querySelector(selector);
const number = (value) => new Intl.NumberFormat("ko-KR").format(value ?? 0);

async function request(url) {
  const response = await fetch(url);
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error || "데이터 요청에 실패했습니다.");
  return payload;
}

async function loadStats() {
  try {
    const stats = await request("/api/stats");
    $("#stat-posts").textContent = number(stats.posts);
    $("#stat-comments").textContent = number(stats.comments);
    $("#stat-users").textContent = number(stats.users);
    $("#stat-views").textContent = number(stats.views);
    $("#stat-range").textContent = `${stats.date_from.slice(0, 10)} — ${stats.date_to.slice(0, 10)}`;
  } catch (error) {
    $("#stat-range").textContent = error.message;
  }
}

function params() {
  const query = new URLSearchParams({ limit: state.limit, offset: (state.page - 1) * state.limit });
  const search = $("#search").value.trim();
  const from = $("#date-from").value;
  const to = $("#date-to").value;
  if (search) query.set("search", search);
  if (from) query.set("from", `${from} 00:00:00`);
  if (to) query.set("to", `${to} 23:59:59`);
  return query;
}

function renderTable(rows) {
  const selected = columns[state.kind];
  const headRow = document.createElement("tr");
  selected.forEach(([, label]) => {
    const th = document.createElement("th");
    th.textContent = label;
    headRow.appendChild(th);
  });
  $("#result-head").replaceChildren(headRow);

  const fragment = document.createDocumentFragment();
  rows.forEach((row) => {
    const tr = document.createElement("tr");
    selected.forEach(([key, , className]) => {
      const td = document.createElement("td");
      td.textContent = row[key] ?? "—";
      if (className) td.className = className;
      tr.appendChild(td);
    });
    fragment.appendChild(tr);
  });
  $("#result-body").replaceChildren(fragment);
}

async function loadRows() {
  $("#page-status").textContent = "데이터를 불러오는 중입니다.";
  try {
    const data = await request(`/api/${state.kind}?${params()}`);
    state.total = data.total;
    const pageCount = Math.max(1, Math.ceil(state.total / state.limit));
    if (state.page > pageCount) {
      state.page = pageCount;
      return loadRows();
    }
    renderTable(data.rows);
    $("#result-total").textContent = number(state.total);
    $("#page-number").textContent = `${number(state.page)} / ${number(pageCount)}`;
    $("#page-status").textContent = `${number((state.page - 1) * state.limit + 1)}–${number(Math.min(state.page * state.limit, state.total))} 표시`;
    $("#prev").disabled = state.page <= 1;
    $("#next").disabled = state.page >= pageCount;
    $("#empty-state").hidden = data.rows.length !== 0;
    $(".table-wrap").hidden = data.rows.length === 0;
  } catch (error) {
    $("#page-status").textContent = error.message;
  }
}

function renderRankings(rows) {
  const fragment = document.createDocumentFragment();
  if (rows.length === 0) {
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.colSpan = 7;
    td.textContent = "조건에 맞는 순위가 없습니다.";
    tr.appendChild(td);
    fragment.appendChild(tr);
  } else {
    rows.forEach((row) => {
      const tr = document.createElement("tr");
      [row.rank, row.nickname, row.user_key, row.post_count, row.comment_count, row.activity_count, row.view_count].forEach((value, index) => {
        const td = document.createElement("td");
        td.textContent = index === 0 ? number(value) : number(value);
        if (index === 1) td.className = "";
        tr.appendChild(td);
      });
      fragment.appendChild(tr);
    });
  }
  $("#ranking-body").replaceChildren(fragment);
}

async function loadRankings() {
  try {
    const query = new URLSearchParams();
    const from = $("#date-from").value;
    const to = $("#date-to").value;
    if (from) query.set("from", `${from} 00:00:00`);
    if (to) query.set("to", `${to} 23:59:59`);
    query.set("limit", "10");
    const data = await request(`/api/rankings?${query}`);
    renderRankings(data.rows);
  } catch (error) {
    $("#ranking-body").innerHTML = `<tr><td colspan="7">${error.message}</td></tr>`;
  }
}

document.querySelectorAll(".segment").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelectorAll(".segment").forEach((item) => item.classList.remove("active"));
    button.classList.add("active");
    state.kind = button.dataset.kind;
    state.page = 1;
    loadRows();
  });
});

$("#filters").addEventListener("submit", (event) => { event.preventDefault(); state.page = 1; loadRows(); loadRankings(); });
$("#reset").addEventListener("click", () => { $("#filters").reset(); state.page = 1; loadRows(); loadRankings(); });
$("#prev").addEventListener("click", () => { if (state.page > 1) { state.page -= 1; loadRows(); } });
$("#next").addEventListener("click", () => { if (state.page * state.limit < state.total) { state.page += 1; loadRows(); } });

loadStats();
loadRows();
loadRankings();
