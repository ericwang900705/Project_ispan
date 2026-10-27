// 共用樣式入口：由主程式與後台殼層共同引用，ES 模組會去除重複載入。
// 管理頁樣式在進入後台時即備妥，不依賴先瀏覽其他管理頁。
import './core.css'
import './layout.css'
import './support.css'
import './theme.css'
import './admin-management.css'
import './support-extension.css'
import './friends.css'
