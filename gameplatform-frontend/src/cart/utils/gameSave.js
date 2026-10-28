// 沙盒遊戲使用獨立記憶體 Storage，經過來源驗證後由外層保存；不開放同源權限。
const MAX_SAVE = 262144
export function validGameSave(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
    && Object.keys(value).length <= 300
    && Object.values(value).every(x => typeof x === 'string')
    && JSON.stringify(value).length <= MAX_SAVE
}
export function readGameSave(key, storage = () => window.localStorage) {
  try { const data = JSON.parse(storage().getItem(key) || '{}'); return validGameSave(data) ? data : {} }
  catch { return {} }
}
export function writeGameSave(key, data, storage = () => window.localStorage) {
  if (!validGameSave(data)) return false
  try { storage().setItem(key, JSON.stringify(data)); return true } catch { return false }
}
export function sandboxGameHtml(html, data, channel) {
  // 避免存檔內容結束 script 標籤；原遊戲仍在 opaque origin 沙盒執行。
  const json = value => JSON.stringify(value).replace(/</g, '\\u003c').replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029')
  const source = `(function(){
    const channel=${json(channel)}, initial=${json(validGameSave(data) ? data : {})};
    function storage(seed,persist){
      const values=new Map(Object.entries(seed));
      return { get length(){return values.size}, key(i){return [...values.keys()][i]??null},
        getItem(k){return values.has(String(k))?values.get(String(k)):null},
        setItem(k,v){k=String(k);v=String(v);const copy=new Map(values);copy.set(k,v);
          if(copy.size>300||JSON.stringify(Object.fromEntries(copy)).length>${MAX_SAVE})throw new DOMException('遊戲存檔已滿','QuotaExceededError');
          values.set(k,v);save()},
        removeItem(k){values.delete(String(k));save()},clear(){values.clear();save()}
      };
      function save(){if(persist)parent.postMessage({type:'hero-game-save',channel,data:Object.fromEntries(values)},'*')}
    }
    Object.defineProperty(window,'localStorage',{value:storage(initial,true),configurable:false});
    Object.defineProperty(window,'sessionStorage',{value:storage({},false),configurable:false});
  })();`
  const bridge = '<script>' + source + '</script>'
  if (/<head\b[^>]*>/i.test(html)) return html.replace(/<head\b[^>]*>/i, tag => tag + bridge)
  if (/<html\b[^>]*>/i.test(html)) return html.replace(/<html\b[^>]*>/i, tag => tag + '<head>' + bridge + '</head>')
  return '<!doctype html><head>' + bridge + '</head>' + html
}
export function gameSaveMessage(event, frameWindow, channel) {
  const data = event.data
  return event.source === frameWindow && data?.type === 'hero-game-save' && data.channel === channel
    && validGameSave(data.data) ? data.data : null
}
