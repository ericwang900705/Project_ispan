// 成功無內容仍回傳 null；代理提示頁及格式錯誤不再露出 JSON.parse 的原始錯誤。
export async function readJsonResponse(response) {
  if (response.status === 204 || response.status === 205) return null
  const text = await response.text()
  if (!text.trim()) return null
  if (/^\s*</.test(text))
    throw Error('API 收到網頁而非資料，請確認 ngrok 與 Nginx 設定，並重新部署前端。')
  try { return JSON.parse(text) }
  catch { throw Error('API 回傳格式錯誤，請確認後端與代理連線。') }
}
