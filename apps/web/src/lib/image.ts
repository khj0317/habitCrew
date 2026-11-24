// 사진을 올리기 전에 브라우저에서 줄인다.
// - 스마트폰 원본(5~10MB)을 그대로 보내면 느리고 데이터가 많이 든다 → 긴 변 1600px JPEG(보통 300~500KB)로 줄여 보낸다
// - 캔버스에 다시 그리므로 촬영 위치(GPS) 같은 EXIF 정보가 함께 지워진다 (서버도 한 번 더 지운다)
// - 아이폰 HEIC처럼 서버가 읽지 못하는 형식도 브라우저가 읽을 수 있으면 JPEG로 바뀐다
// - createImageBitmap은 EXIF 회전 정보를 반영해 그리므로 세로 사진이 눕지 않는다

const MAX_SIDE = 1600

export async function resizeForUpload(file: File): Promise<Blob> {
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  } catch {
    throw new Error('이 사진은 열 수 없어요. 다른 사진을 골라 주세요.')
  }
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height))
  const width = Math.round(bitmap.width * scale)
  const height = Math.round(bitmap.height * scale)

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#fff' // 투명한 PNG를 JPEG로 바꿀 때 검게 나오지 않게
  ctx.fillRect(0, 0, width, height)
  ctx.drawImage(bitmap, 0, 0, width, height)
  bitmap.close()

  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('사진을 준비하지 못했어요.'))), 'image/jpeg', 0.88),
  )
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)}KB`
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`
}
