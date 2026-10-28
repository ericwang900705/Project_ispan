// 允許成功但無內容的 API 回應；有內容時仍嚴格解析 JSON。
export async function readJsonResponse(response) {
  if (response.status === 204 || response.status === 205) return null
  const text = await response.text()
  return text.trim() ? JSON.parse(text) : null
}
