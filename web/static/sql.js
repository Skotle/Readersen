const $ = (selector) => document.querySelector(selector);

const examples = {
  latest: `SELECT nickname, user_key, created_at, view_count, recommend_count, comment_count
FROM posts
ORDER BY created_at DESC
LIMIT 50;`,
  users: `SELECT
  user_key,
  max(nickname) AS latest_nickname,
  count(*) AS total_events,
  sum(event_type = 'post') AS posts,
  sum(event_type = 'comment') AS comments
FROM activity_events
GROUP BY user_key
ORDER BY total_events DESC
LIMIT 100;`,
  daily: `SELECT activity_date, post_count, comment_count, active_users
FROM daily_summary
ORDER BY activity_date DESC
LIMIT 100;`
};

async function loadSchema() {
  try {
    const response = await fetch("/api/schema");
    const data = await response.json();
    const fragment = document.createDocumentFragment();
    data.objects.forEach((object) => {
      const details = document.createElement("details");
      details.className = "schema-object";
      const summary = document.createElement("summary");
      summary.append(document.createTextNode(object.name));
      const type = document.createElement("span");
      type.textContent = object.type.toUpperCase();
      summary.appendChild(type);
      const pre = document.createElement("pre");
      pre.textContent = object.sql;
      details.append(summary, pre);
      fragment.appendChild(details);
    });
    $("#schema-list").replaceChildren(fragment);
  } catch {
    $("#schema-list").textContent = "스키마를 불러오지 못했습니다.";
  }
}

function renderResult(data) {
  const headRow = document.createElement("tr");
  data.columns.forEach((column) => {
    const th = document.createElement("th");
    th.textContent = column;
    headRow.appendChild(th);
  });
  $("#sql-head").replaceChildren(headRow);

  const fragment = document.createDocumentFragment();
  data.rows.forEach((row) => {
    const tr = document.createElement("tr");
    row.forEach((value) => {
      const td = document.createElement("td");
      td.textContent = value === null ? "NULL" : value;
      tr.appendChild(td);
    });
    fragment.appendChild(tr);
  });
  $("#sql-body").replaceChildren(fragment);
  $("#sql-empty").hidden = data.rows.length > 0;
  $(".sql-results").hidden = data.rows.length === 0;
  $("#query-stats").textContent = `${data.rowCount}행 · ${data.elapsedMs}ms${data.truncated ? " · 500행에서 잘림" : ""}`;
}

async function runQuery() {
  const sql = $("#sql-editor").value.trim();
  if (!sql) return;
  const button = $("#run-query");
  button.disabled = true;
  $("#query-message").textContent = "쿼리를 실행하고 있습니다…";
  try {
    const response = await fetch("/api/query", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sql })
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "쿼리 실행에 실패했습니다.");
    renderResult(data);
    $("#query-message").textContent = "쿼리가 정상적으로 실행되었습니다.";
  } catch (error) {
    $("#query-message").textContent = `오류: ${error.message}`;
    $("#query-stats").textContent = "실행 실패";
  } finally {
    button.disabled = false;
  }
}

document.querySelectorAll("[data-query]").forEach((button) => {
  button.addEventListener("click", () => { $("#sql-editor").value = examples[button.dataset.query]; });
});
$("#run-query").addEventListener("click", runQuery);
$("#sql-editor").addEventListener("keydown", (event) => {
  if ((event.ctrlKey || event.metaKey) && event.key === "Enter") { event.preventDefault(); runQuery(); }
  if (event.key === "Tab") {
    event.preventDefault();
    const start = event.target.selectionStart;
    event.target.setRangeText("  ", start, event.target.selectionEnd, "end");
  }
});

loadSchema();
