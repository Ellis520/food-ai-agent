export async function imagePreview(
  file: File,
  maxSide = 1280,
  maxChars = 1_000_000,
) {
  if (
    !["image/jpeg", "image/png", "image/webp"].includes(file.type) ||
    file.size > 10 * 1024 * 1024
  ) {
    throw new Error("请选择 10 MB 以内的 JPG、PNG 或 WebP 图片。");
  }
  const bitmap = await createImageBitmap(file).catch(() => {
    throw new Error("图片无法读取，请换一张。");
  });
  try {
    const ratio = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(bitmap.width * ratio));
    canvas.height = Math.max(1, Math.round(bitmap.height * ratio));
    const context = canvas.getContext("2d");
    if (!context) throw new Error("当前浏览器无法处理这张图片。");
    context.fillStyle = "#fff";
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    for (const quality of [0.84, 0.65, 0.45]) {
      const url = canvas.toDataURL("image/jpeg", quality);
      if (url.startsWith("data:image/jpeg;base64,") && url.length <= maxChars)
        return url;
    }
    throw new Error("图片压缩后仍过大，请换一张。");
  } finally {
    bitmap.close();
  }
}
