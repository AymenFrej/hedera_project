import { useState } from 'react'
type Props = { agentName: string; placeholder: string }
export default function AgentCommandBox({ agentName, placeholder }: Props) {
  const [value, setValue] = useState('')
  const [sent, setSent] = useState(false)
  return <div className="command-box"><div className="command-label"><span className="agent-dot"/> {agentName} <span className="mock-pill">MOCK</span></div><div className="command-row"><input value={value} onChange={e => { setValue(e.target.value); setSent(false) }} placeholder={placeholder}/><button onClick={() => { if (value.trim()) setSent(true) }}>Send</button></div>{sent && <div className="command-response">Mock response queued. Connect this command box to the module agent when ready.</div>}</div>
}
