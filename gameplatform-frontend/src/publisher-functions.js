export const publisherFunctions = [
  {
    mode: 'preview',
    label: '上架預覽',
    description: '預覽已儲存的封面、介紹、售價與宣傳素材。'
  },
  {
    mode: 'sales',
    label: '銷售統計',
    description: '依日期與遊戲查看銷售金額、份數與退款。'
  },
  {
    mode: 'new',
    label: '新增遊戲',
    description: '建立遊戲草稿，填寫名稱、售價與介紹。'
  },
  {
    mode: 'discounts',
    label: '遊戲折扣管理',
    description: '設定 1～99% 整數折扣，固定七天，結束後冷卻三十天。'
  },
  {
    mode: 'edit',
    label: '修改遊戲',
    description: '編輯草稿或已下架遊戲。'
  },
  {
    mode: 'publish',
    label: '送出上架審核',
    description: '確認版本與遊戲資料，再送交審核。'
  },
  {
    mode: 'list',
    label: '查詢自己的遊戲',
    description: '搜尋遊戲並查看目前上架與申請狀態。'
  },
  {
    mode: 'history',
    label: '查看審核歷史與拒絕原因',
    description: '查看每次處理結果、審核人員與原因。'
  },
  {
    mode: 'offShelf',
    label: '下架與取消下架',
    description: '申請下架後，24 小時內可取消，期限到後自動下架。'
  },
  {
    mode: 'builds',
    label: '遊戲版本資訊管理',
    description: '維護版本號，上傳 ZIP 或填寫檔案網址，並設定啟用狀態。'
  },
  {
    mode: 'tags',
    label: '遊戲標籤管理',
    description: '從現有標籤中設定遊戲類型。'
  },
  {
    mode: 'media',
    label: '遊戲圖片與影片管理',
    description: '前往修改遊戲，上傳封面、圖片與 MP4 影片並調整順序。'
  },
  {
    mode: 'buyers',
    label: '購買人數',
    description: '查看每款遊戲已付款的不重複購買會員數。'
  }
]

export const publisherLink = mode =>
  ['sales', 'discounts'].includes(mode)
    ? { path: mode === 'sales' ? '/publisher/sales' : '/publisher/discounts' }
    : { path: '/publisher/games', query: { mode } }

// 呈現分組沿用相同功能模式及路由。
export const publisherGroups = [
  {
    title: '遊戲管理',
    icon: 'game',
    modes: ['list', 'new', 'edit', 'discounts', 'preview', 'offShelf']
  },
  {
    title: '素材與版本',
    icon: 'folder',
    modes: ['builds', 'tags', 'media']
  },
  {
    title: '上架與數據',
    icon: 'trend',
    modes: ['publish', 'history', 'buyers', 'sales']
  }
]
export const publisherIcons = {
  preview: 'image',
  sales: 'trend',
  list: 'game',
  new: 'plus',
  edit: 'edit',
  discounts: 'tag',
  offShelf: 'power',
  builds: 'folder',
  tags: 'tag',
  media: 'image',
  publish: 'shield',
  history: 'file',
  buyers: 'users'
}