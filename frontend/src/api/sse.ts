import { ApiError, apiFetch } from './client'; import type { StreamEvent } from './types'
export interface SseFrame { id?: string; event?: string; data: string }
export async function parseSse(stream: ReadableStream<Uint8Array>, receive: (frame: SseFrame) => void) {
  const reader = stream.getReader(), decoder = new TextDecoder(); let buffer = ''
  while (true) { const { done, value } = await reader.read(); buffer += decoder.decode(value, { stream: !done }); const blocks = buffer.split(/\r?\n\r?\n/); buffer = blocks.pop() ?? ''; for (const block of blocks) parseBlock(block, receive); if (done) break }
  if (buffer.trim()) parseBlock(buffer, receive)
}
function parseBlock(block: string, receive: (frame: SseFrame) => void) { let id: string | undefined, event: string | undefined; const data: string[] = []; for (const line of block.split(/\r?\n/)) { if (!line || line.startsWith(':')) continue; const index = line.indexOf(':'); const field = index < 0 ? line : line.slice(0, index); const value = index < 0 ? '' : line.slice(index + 1).replace(/^ /, ''); if (field === 'id') id = value; else if (field === 'event') event = value; else if (field === 'data') data.push(value) } if (data.length) receive({ id, event, data: data.join('\n') }) }
export async function consumeStream(path: string, init: RequestInit, onEvent: (event: StreamEvent) => void,onOpen?: (response:Response)=>void) {
  const response = await apiFetch(path,init,true,15_000)
  onOpen?.(response)
  if (!response.body) throw new ApiError(response.status, 'STREAM_FAILED', '咨询流没有响应内容')
  await parseSse(response.body, frame => { if (!['ack', 'delta', 'done', 'error'].includes(frame.event ?? '')) return; try { onEvent({ id: frame.id ? Number(frame.id) : undefined, type: frame.event, data: JSON.parse(frame.data) } as StreamEvent) } catch { throw new ApiError(0, 'AI_OUTPUT_INVALID', '收到无效流数据') } })
}
