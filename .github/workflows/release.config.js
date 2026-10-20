// release.config.js
// Not: Bu dosya CommonJS (.js) formatındadır, ESM kullanıyorsan en alttaki nota bak.

module.exports = {
  branches: ['main'],

  plugins: [
    // 1) Commit'leri analiz et → sürüm tipini belirle (patch/minor/major)
    '@semantic-release/commit-analyzer',

    // 2) Sürüm notlarını üret (GitHub Release body'si ve CHANGELOG.md için)
    '@semantic-release/release-notes-generator',

    // 3) CHANGELOG.md dosyasını güncelle
    [
      '@semantic-release/changelog',
      {
        changelogFile: 'CHANGELOG.md',
        changelogTitle: '# Changelog\n\nTüm önemli değişiklikler bu dosyada belgelenir.'
      }
    ],

    // 4) CHANGELOG.md'yi commit'le
    // ⚠️ E2BIG ÇÖZÜMÜ: mesajda ${nextRelease.notes} YOK.
    // Notlar CHANGELOG.md içinde ve GitHub Release'de duruyor,
    // sadece commit mesajı kısa tutuluyor.
    [
      '@semantic-release/git',
      {
        assets: ['CHANGELOG.md'],
        message: 'chore(release): ${nextRelease.version} [skip ci]'
      }
    ],

    // 5) GitHub Release oluştur (tag'i semantic-release core zaten atıyor)
    '@semantic-release/github'
  ]
};