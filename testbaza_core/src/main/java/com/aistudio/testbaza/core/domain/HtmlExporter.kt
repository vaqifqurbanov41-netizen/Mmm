package com.aistudio.testbaza.core.domain

import org.json.JSONArray
import org.json.JSONObject

object HtmlExporter {

    fun safeExportFileName(title: String): String {
        val cleaned = title
            .replace(Regex("""[^a-zA-Z0-9ƏəÖöÜüĞğÇçŞşİı_-]+"""), "_")
            .trim('_')
        return "${cleaned.ifEmpty { "TestBaza_Export" }}.html"
    }

    fun buildStandaloneHtml(bankTitle: String, questions: List<QuizQuestion>): String {
        val jsonArray = JSONArray()
        for (q in questions) {
            val obj = JSONObject().apply {
                put("id", q.questionNumber)
                put("cat", q.category)
                put("isExam", q.isExam)
                put("q", q.questionText)
                put("allOptions", JSONArray(q.allOptions))
                put("correctText", q.correctText)
                put("hasConfirmedKey", q.hasConfirmedKey)
            }
            jsonArray.put(obj)
        }
        val escapedTitle = escapeHtml(bankTitle)
        val embeddedJson = jsonArray.toString(2).replace("</script>", "<\\/script>")
        val quotedTitle = JSONObject.quote(bankTitle)

        return """
<!DOCTYPE html>
<html lang="az">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>$escapedTitle — TestBaza</title>
<style>
  :root { --bg:#070B12; --card:#101826; --border:#1E2A3D; --text:#F3F5F8; --muted:#9AA5B4; --accent:#10B981; --gold:#E5C158; --danger:#EF4444; }
  * { box-sizing:border-box; }
  body { margin:0; font-family:system-ui,-apple-system,sans-serif; background:var(--bg); color:var(--text); padding:16px; }
  .wrap { max-width:760px; margin:0 auto; }
  .header { display:flex; flex-wrap:wrap; justify-content:space-between; align-items:center; gap:12px; padding:16px; background:var(--card); border:1px solid var(--border); border-radius:18px; margin-bottom:16px; }
  .title { font-size:18px; font-weight:700; color:var(--gold); }
  .meta { font-size:13px; color:var(--muted); margin-top:4px; }
  .btn { border:1px solid var(--border); background:#162235; color:var(--text); border-radius:12px; padding:10px 14px; font-weight:600; cursor:pointer; }
  .btn-primary { background:var(--accent); color:#042419; border-color:var(--accent); }
  .card { background:var(--card); border:1px solid var(--border); border-radius:20px; padding:20px; }
  .qnum { font-size:12px; font-weight:700; color:var(--gold); letter-spacing:.06em; text-transform:uppercase; }
  .qtext { font-size:18px; font-weight:600; line-height:1.45; margin:10px 0 18px; }
  .opt { width:100%; text-align:left; display:flex; gap:12px; align-items:flex-start; padding:14px; border-radius:14px; border:1px solid var(--border); background:#0C1320; color:var(--text); margin-bottom:10px; font-size:15px; cursor:pointer; }
  .opt.correct { background:#064E3B; border-color:var(--accent); }
  .opt.wrong { background:#450A0A; border-color:var(--danger); }
  .badge { min-width:26px; height:26px; border-radius:8px; background:#162235; display:inline-flex; align-items:center; justify-content:center; font-weight:700; font-size:13px; color:var(--gold); }
  .nav { display:flex; justify-content:space-between; gap:10px; margin-top:16px; }
</style>
</head>
<body>
<div class="wrap">
  <div class="header">
    <div>
      <div class="title">$escapedTitle</div>
      <div class="meta" id="metaLine"></div>
    </div>
    <button class="btn btn-primary" onclick="restart(true)">Qarışdır və Başla</button>
  </div>
  <div class="card">
    <div class="qnum" id="qNum"></div>
    <div class="qtext" id="qText"></div>
    <div id="opts"></div>
    <div class="nav">
      <button class="btn" onclick="prev()">Əvvəlki</button>
      <button class="btn btn-primary" onclick="next()">Növbəti</button>
    </div>
  </div>
</div>
<script id="embeddedDataScript">
const INITIAL_QUESTIONS = $embeddedJson;
const INITIAL_TITLE = $quotedTitle;
</script>
<script>
const letters = ['A','B','C','D','E','F'];
let session = [];
let idx = 0;
let ok = 0;
let bad = 0;
let answers = {};
function shuffle(a){ const r=[...a]; for(let i=r.length-1;i>0;i--){ const j=Math.floor(Math.random()*(i+1)); [r[i],r[j]]=[r[j],r[i]]; } return r; }
function restart(doShuffle){
  const list = doShuffle ? shuffle(INITIAL_QUESTIONS) : [...INITIAL_QUESTIONS];
  session = list.map(q => {
    const opts = doShuffle ? shuffle(q.allOptions) : [...q.allOptions];
    const target = q.correctText || q.allOptions[0];
    return { ...q, opts, correctIdx: Math.max(0, opts.indexOf(target)) };
  });
  idx = 0; ok = 0; bad = 0; answers = {};
  render();
}
function render(){
  const q = session[idx];
  if(!q) return;
  document.getElementById('metaLine').textContent = (idx+1) + ' / ' + session.length + ' • Düz: ' + ok + ' • Səhv: ' + bad;
  document.getElementById('qNum').textContent = 'Sual #' + q.id;
  document.getElementById('qText').textContent = q.q;
  const wrap = document.getElementById('opts');
  wrap.innerHTML = '';
  const rec = answers[idx];
  q.opts.forEach((o, i) => {
    const b = document.createElement('button');
    b.className = 'opt';
    if(rec !== undefined){
      b.disabled = true;
      if(i === q.correctIdx) b.classList.add('correct');
      else if(i === rec) b.classList.add('wrong');
    } else {
      b.onclick = () => {
        answers[idx] = i;
        if(i === q.correctIdx) ok++; else bad++;
        render();
      };
    }
    const s1 = document.createElement('span'); s1.className='badge'; s1.textContent=letters[i]||(i+1);
    const s2 = document.createElement('span'); s2.textContent=o;
    b.appendChild(s1); b.appendChild(s2); wrap.appendChild(b);
  });
}
function prev(){ if(idx>0){ idx--; render(); } }
function next(){ if(idx<session.length-1){ idx++; render(); } }
restart(true);
</script>
</body>
</html>
        """.trimIndent()
    }

    private fun escapeHtml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
