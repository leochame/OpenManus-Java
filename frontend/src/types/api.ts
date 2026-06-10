export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'error';

export interface WebPreviewDiagnosticPayload {
  enabled: boolean;
  targetUrl: string;
  proxyUrl: string;
  state: string;
  reasonCode: string;
  reason: string;
  previewMode: 'web' | 'proxy' | 'vnc' | 'external';
  redirectLocation?: string;
  contentType?: string;
  previewableHtml: boolean;
  fallbackToVnc: boolean;
}

export interface StreamStartResponse {
  success: boolean;
  session_id?: string;
  sessionId?: string;
  executionId?: string;
  topic?: string;
  error?: string;
  errorCode?: string;
}

export interface ExecutionEventPayload {
  session_id?: string;
  sessionId?: string;
  event_id?: string;
  eventId?: string;
  agent_name?: string;
  agentName?: string;
  agent_type?: string;
  agentType?: string;
  event_type?: string;
  eventType?: string;
  status?: string;
  input?: unknown;
  output?: unknown;
  error?: string;
  start_time?: string;
  startTime?: string;
  end_time?: string;
  endTime?: string;
  duration?: number;
  metadata?: Record<string, unknown>;
}

export interface ExecutionLogPayload {
  session_id?: string;
  sessionId?: string;
  timestamp?: string;
  level?: string;
  thread?: string;
  logger?: string;
  message?: string;
}

export interface WorkflowResultPayload {
  session_id?: string;
  sessionId?: string;
  messageType?: string;
  user_input?: string;
  userInput?: string;
  result?: string;
  status?: string;
  completed_time?: string;
  completedTime?: string;
  execution_time?: number;
  executionTime?: number;
}

export interface SessionInfoPayload {
  sessionId: string;
  sandboxVncUrl?: string;
  sandboxContainerId?: string;
  sandboxStatus?: string;
  sandboxCreatedAt?: string;
  sandboxAvailable?: boolean;
}

export interface WorkflowRequestPayload {
  input: string;
  sessionId?: string;
  agentTeam?: boolean;
  agentTeamCoding?: boolean;
  targetRepositoryPath?: string;
}

export interface ThoughtStep {
  id: string;
  time: string;
  kind: 'llm_request' | 'llm_response' | 'tool_start' | 'tool_end' | 'iteration' | 'status' | 'error' | 'result';
  title: string;
  content: string;
  status: string;
  agentName: string;
}
