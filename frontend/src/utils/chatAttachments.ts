/** Must match {@code ChatService.mergeUserMessageBody} delimiter. */
export const USER_ATTACHMENT_SECTION = '\n\n--- 附件 ---'

export interface ParsedChatAttachment {
  fileName: string
  text: string
}

/** Text shown in the user bubble (hides merged attachment bodies). */
export function userBubbleVisibleText(content: string): string {
  const i = content.indexOf(USER_ATTACHMENT_SECTION)
  if (i === -1) return content
  return content.slice(0, i).trimEnd()
}

/**
 * Parses per-file extracted bodies from persisted user {@code content} after the attachment marker.
 */
export function parseUserAttachmentBodies(content: string): ParsedChatAttachment[] {
  const i = content.indexOf(USER_ATTACHMENT_SECTION)
  if (i === -1) return []
  let rest = content.slice(i + USER_ATTACHMENT_SECTION.length)
  const out: ParsedChatAttachment[] = []
  while (rest.length > 0) {
    const m = /^\n\n--- (.+?) ---\n/.exec(rest)
    if (!m) break
    const fileName = m[1]
    rest = rest.slice(m[0].length)
    const next = rest.search(/\n\n--- /)
    const text = (next === -1 ? rest : rest.slice(0, next)).trimEnd()
    out.push({ fileName, text })
    if (next === -1) break
    rest = rest.slice(next)
  }
  return out
}

/** True if MIME or file extension indicates an image (browser MIME is often empty). */
export function isLikelyImageAttachment(mime: string | null | undefined, fileName: string): boolean {
  const m = (mime ?? '').toLowerCase()
  if (m.startsWith('image/')) return true
  return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(fileName)
}
