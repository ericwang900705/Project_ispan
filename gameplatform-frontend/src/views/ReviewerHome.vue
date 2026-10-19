<template>
    <section class="page">
        <h1>審核人員主頁</h1>
        <p class="muted">
            HERO 遊戲平台｜遊戲審核工作區
        </p>

        <div class="reviewer-actions">
            <RouterLink to="/admin/games?mode=pending" class="primary">
                待審核遊戲
            </RouterLink>

            <RouterLink to="/admin/games?mode=history">
                查看審核紀錄
            </RouterLink>
        </div>
    </section>
</template>

<style scoped>
.reviewer-actions {
    display: flex;
    gap: 16px;
    margin-top: 24px;
    flex-wrap: wrap;
}
</style>
