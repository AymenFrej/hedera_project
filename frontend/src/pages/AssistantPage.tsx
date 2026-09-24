import { Bot } from 'lucide-react'

export default function AssistantPage() {
  return <>
    <div className="page-heading">
      <div>
        <p className="eyebrow">ASSISTANT</p>
        <h1>AI Assistant</h1>
        <p className="subtitle">Your assistant for interacting with and understanding Hedera.</p>
      </div>
    </div>
    <section className="panel assistant-empty" aria-label="AI Assistant placeholder">
      <div className="empty-icon"><Bot size={20}/></div>
      <h2>AI Assistant is being prepared</h2>
      <p>This space will help you explore Hedera and your workspace.</p>
      <div className="assistant-compose" aria-label="Chat input unavailable">
        <input type="text" placeholder="Chat will be available here" disabled aria-label="Chat message" />
        <button className="button primary" type="button" disabled>Send</button>
      </div>
    </section>
  </>
}
