import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { BookOpen, Bot, ExternalLink, Send, UserRound } from 'lucide-react'
import { sendAssistantMessage } from '../api/client'
import type { AssistantChatSource } from '../api/client'

type ChatMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  sources?: AssistantChatSource[]
  error?: boolean
}

const welcomeMessage: ChatMessage = {
  id: 'assistant-welcome',
  role: 'assistant',
  content: "Hello! I'm your Hedera Assistant. I can help you explore Hedera concepts and this workspace. What would you like to know?",
}

function createMessageId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function isOfficialHederaSource(url: string) {
  try { return new URL(url).hostname === 'docs.hedera.com' } catch { return false }
}

function sourcesInAnswer(content: string, sources: AssistantChatSource[] = []) {
  const cited = [...content.matchAll(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g)]
    .filter(([, , url]) => isOfficialHederaSource(url))
    .map(([, title, url]) => ({ title, url }))
  const unique = new Map(cited.map(source => [source.url.replace(/\/$/, ''), source]))
  for (const source of sources) unique.set(source.url.replace(/\/$/, ''), source)
  return [...unique.values()]
}

function removeInlineSourceCitations(content: string, sources: AssistantChatSource[] = []) {
  let answer = content.replace(/<cite>[\s\S]*?<\/cite>/gi, '')
  answer = answer.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, (citation, _title: string, url: string) =>
    isOfficialHederaSource(url) ? '' : citation)
  for (const url of new Set(sources.map(source => source.url))) {
    answer = answer.replace(new RegExp(`\\[[^\\]]+\\]\\(${escapeRegExp(url)}\\)`, 'gi'), '')
  }
  return answer
    .replace(/^\s*(?:\*\*)?Sources?(?:\*\*)?\s*:?\s*$/gim, '')
    .replace(/\(\s*(?:[;,\s])*\)/g, '')
    .replace(/\s+([;,])/g, '$1')
    .replace(/[ \t]+([,.;:!?])/g, '$1')
    .replace(/[ \t]{2,}/g, ' ')
    .trim()
}

function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const tokenPattern = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\(https?:\/\/[^\s)]+\))/g
  return text.split(tokenPattern).filter(Boolean).map((piece, index) => {
    const key = `${keyPrefix}-${index}`
    if (piece.startsWith('**') && piece.endsWith('**')) return <strong key={key}>{piece.slice(2, -2)}</strong>
    if (piece.startsWith('`') && piece.endsWith('`')) return <code key={key}>{piece.slice(1, -1)}</code>
    const link = piece.match(/^\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)$/)
    if (link) return <a key={key} href={link[2]} target="_blank" rel="noreferrer">{link[1]}</a>
    return piece
  })
}

function tableCells(line: string) {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim())
}

function sourceHost(url: string) {
  try { return new URL(url).hostname.replace(/^www\./, '') } catch { return url }
}

function AssistantAnswer({ content, sources }: { content: string; sources?: AssistantChatSource[] }) {
  const visibleSources = sourcesInAnswer(content, sources)
  const lines = removeInlineSourceCitations(content, visibleSources).split('\n')
  const blocks: ReactNode[] = []
  let paragraph: string[] = []
  let items: string[] = []
  let listType: 'ul' | 'ol' | null = null
  let key = 0

  const flushParagraph = () => {
    if (!paragraph.length) return
    const text = paragraph.join(' ').trim()
    const sentences = text.match(/[^.!?]+(?:[.!?]+(?=\s|$)|$)/g)?.map(sentence => sentence.trim()).filter(Boolean) ?? [text]
    if (text.length > 180 && sentences.length > 1) {
      blocks.push(<p className="assistant-answer-lead" key={`lead-${key++}`}>{renderInline(sentences[0], `lead-${key}`)}</p>)
      blocks.push(<div className="assistant-key-points" key={`details-${key++}`}>
        <strong>Key details</strong>
        <ul>{sentences.slice(1).map((sentence, index) => <li key={index}>{renderInline(sentence, `detail-${key}-${index}`)}</li>)}</ul>
      </div>)
    } else {
      blocks.push(<p key={`p-${key++}`}>{renderInline(text, `p-${key}`)}</p>)
    }
    paragraph = []
  }
  const flushList = () => {
    if (!items.length || !listType) return
    const List = listType
    blocks.push(<List key={`list-${key++}`}>{items.map((item, i) => <li key={i}>{renderInline(item, `li-${key}-${i}`)}</li>)}</List>)
    items = []
    listType = null
  }

  for (let lineIndex = 0; lineIndex < lines.length; lineIndex++) {
    const line = lines[lineIndex].trim()
    if (!line) { flushParagraph(); flushList(); continue }

    const heading = line.match(/^(#{1,3})\s+(.+)$/)
    if (heading) {
      flushParagraph(); flushList()
      const title = renderInline(heading[2], `heading-${key}`)
      blocks.push(heading[1].length === 1
        ? <h2 key={`heading-${key++}`}>{title}</h2>
        : <h3 key={`heading-${key++}`}>{title}</h3>)
      continue
    }

    const next = lines[lineIndex + 1]?.trim() ?? ''
    if (line.includes('|') && /^\|?\s*:?-{3,}/.test(next)) {
      flushParagraph(); flushList()
      const headers = tableCells(line)
      const rows: string[][] = []
      lineIndex += 2
      while (lineIndex < lines.length && lines[lineIndex].includes('|')) rows.push(tableCells(lines[lineIndex++]))
      lineIndex--
      blocks.push(<div className="chat-table-wrap" key={`table-${key++}`}><table>
        <thead><tr>{headers.map((cell, i) => <th key={i}>{renderInline(cell, `th-${i}`)}</th>)}</tr></thead>
        <tbody>{rows.map((row, i) => <tr key={i}>{headers.map((_, j) => <td key={j}>{renderInline(row[j] ?? '', `td-${i}-${j}`)}</td>)}</tr>)}</tbody>
      </table></div>)
      continue
    }

    const unordered = line.match(/^[-*+]\s+(.+)$/)
    const ordered = line.match(/^\d+[.)]\s+(.+)$/)
    if (unordered || ordered) {
      flushParagraph()
      const type = ordered ? 'ol' : 'ul'
      if (listType !== type) flushList()
      listType = type
      items.push((unordered ?? ordered)![1])
      continue
    }

    flushList()
    const quote = line.match(/^>\s?(.*)$/)
    if (quote) {
      flushParagraph()
      blocks.push(<blockquote key={`quote-${key++}`}>{renderInline(quote[1], `quote-${key}`)}</blockquote>)
      continue
    }
    paragraph.push(line)
  }
  flushParagraph()
  flushList()

  return <div className="assistant-answer">
    {blocks}
    {visibleSources.length > 0 && <div className="chat-sources">
      <div className="chat-sources-heading">
        <span className="chat-sources-icon"><BookOpen size={14}/></span>
        <div><strong>Information from official Hedera documentation</strong><span>These pages informed this answer</span></div>
      </div>
      <ul>{visibleSources.map(source => <li key={source.url}>
        <a className="chat-source-card" href={source.url} target="_blank" rel="noreferrer">
          <span className="chat-source-copy"><strong>{source.title}</strong><small>{sourceHost(source.url)}</small></span>
          <span className="chat-source-action">Open page <ExternalLink size={12}/></span>
        </a>
      </li>)}</ul>
    </div>}
  </div>
}

export default function AssistantPage() {
  const [messages, setMessages] = useState<ChatMessage[]>([welcomeMessage])
  const [input, setInput] = useState('')
  const [thinking, setThinking] = useState(false)
  const conversationRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const conversation = conversationRef.current
    conversation?.scrollTo({ top: conversation.scrollHeight, behavior: 'smooth' })
  }, [messages, thinking])

  async function sendMessage(event?: FormEvent) {
    event?.preventDefault()
    const content = input.trim()
    if (!content || thinking) return

    setMessages(current => [...current, { id: createMessageId(), role: 'user', content }])
    setInput('')
    setThinking(true)

    try {
      const response = await sendAssistantMessage(content)
      setMessages(current => [...current, {
        id: createMessageId(),
        role: 'assistant',
        content: response.message,
        sources: response.sources,
      }])
    } catch (error) {
      const detail = error instanceof Error ? error.message : 'Please try again.'
      setMessages(current => [...current, {
        id: createMessageId(),
        role: 'assistant',
        content: `I couldn't get a response. ${detail}`,
        error: true,
      }])
    } finally {
      setThinking(false)
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      void sendMessage()
    }
  }

  return <>
    <div className="page-heading">
      <div>
        <p className="eyebrow">ASSISTANT</p>
        <h1>AI Assistant</h1>
        <p className="subtitle">Your assistant for interacting with and understanding Hedera.</p>
      </div>
    </div>

    <section className="assistant-chat panel" aria-label="AI Assistant chat">
      <header className="assistant-chat-header">
        <div className="assistant-chat-title"><span className="assistant-header-icon"><Bot size={17}/></span><div><h2>Hedera Assistant</h2><span>Here to help with your Hedera workspace</span></div></div>
        <span className="assistant-demo-status"><i/> Documentation grounded</span>
      </header>

      <div className="assistant-conversation" ref={conversationRef} role="log" aria-live="polite" aria-label="Conversation">
        {messages.map(message => <div className={`chat-message ${message.role}${message.error ? ' error' : ''}`} key={message.id}>
          <div className="chat-avatar" aria-hidden="true">{message.role === 'assistant' ? <Bot size={16}/> : <UserRound size={16}/>}</div>
          <div className="chat-message-content">
            <span className="chat-message-author">{message.error ? 'Assistant · unavailable' : message.role === 'assistant' ? 'Assistant' : 'You'}</span>
            {message.role === 'assistant' && !message.error
              ? <AssistantAnswer content={message.content} sources={message.sources}/>
              : <div className="chat-user-content">{message.content}</div>}
          </div>
        </div>)}
        {thinking && <div className="chat-message assistant" aria-label="Assistant is thinking">
          <div className="chat-avatar" aria-hidden="true"><Bot size={16}/></div>
          <div className="chat-message-content"><span className="chat-message-author">Assistant</span><p className="chat-thinking"><i/><i/><i/><span>Thinking</span></p></div>
        </div>}
      </div>

      <form className="assistant-chat-form" onSubmit={event => void sendMessage(event)}>
        <label className="assistant-input-label" htmlFor="assistant-message">Message</label>
        <div className="assistant-input-row">
          <textarea id="assistant-message" value={input} onChange={event => setInput(event.target.value)} onKeyDown={handleKeyDown} placeholder="Ask a question about Hedera…" rows={1} disabled={thinking} />
          <button className="button primary assistant-send" type="submit" disabled={!input.trim() || thinking} aria-label="Send message"><Send size={15}/><span>Send</span></button>
        </div>
        <p className="assistant-input-hint">Enter to send · Shift+Enter for a new line</p>
      </form>
    </section>
  </>
}
