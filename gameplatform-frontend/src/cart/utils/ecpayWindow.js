// 在使用者點擊時先開付款分頁，再向後端取簽章表單，避免非同步請求後被瀏覽器擋下。
const gateways = new Set([
  'https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5',
  'https://payment.ecpay.com.tw/Cashier/AioCheckOut/V5'
])
export async function openEcpayPayment(createPayment) {
  const target = `hero-payment-${crypto.randomUUID()}`
  const tab = window.open('about:blank', target)
  if (!tab) throw new Error('付款分頁被瀏覽器阻擋，請允許此網站開啟新分頁後再試一次。')
  tab.opener = null
  tab.document.title = '前往綠界付款'
  tab.document.body.style.cssText = 'font:16px system-ui;padding:48px;color:#243e52;background:#edf5fa'
  tab.document.body.textContent = '正在準備付款資料，請稍候…'
  try {
    const data = await createPayment()
    if (tab.closed) throw new Error('付款分頁已關閉，請到「我的訂單」繼續付款。')
    if (!gateways.has(data.actionUrl) || !data.params || typeof data.params !== 'object')
      throw new Error('付款網址或表單資料無效，請稍後再試。')
    const form = tab.document.createElement('form')
    form.method = 'POST'; form.action = data.actionUrl; form.target = '_self'
    for (const [name, value] of Object.entries(data.params)) {
      const input = tab.document.createElement('input')
      input.type = 'hidden'; input.name = name; input.value = String(value)
      form.appendChild(input)
    }
    tab.document.body.appendChild(form)
    try { form.submit() } finally { form.remove() }
    return data
  } catch (error) {
    tab.close()
    throw error
  }
}
