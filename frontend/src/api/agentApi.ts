import type {
  ExecutionEventPayload,
  SessionInfoPayload,
  StreamStartResponse,
  WebPreviewDiagnosticPayload,
  WorkflowRequestPayload
} from '../types/api';

export class ApiError extends Error {
  readonly code?: string;
  readonly status?: number;

  constructor(message: string, options?: { code?: string; status?: number }) {
    super(message);
    this.code = options?.code;
    this.status = options?.status;
  }
}

export async function startWorkflow(payload: WorkflowRequestPayload): Promise<StreamStartResponse> {
  const agentTeam = payload.agentTeam === true;
  const agentTeamCoding = payload.agentTeamCoding === true;
  const requestPayload = {
    input: payload.input,
    sessionId: payload.sessionId,
    targetRepositoryPath: payload.targetRepositoryPath
  };
  const params = new URLSearchParams();
  if (agentTeam) {
    params.set('agentTeam', 'true');
  }
  if (agentTeamCoding) {
    params.set('agentTeamCoding', 'true');
  }
  const suffix = params.size > 0 ? '?' + params.toString() : '';
  const response = await fetch('/api/agent/workflow-stream' + suffix, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(requestPayload)
  });

  const data = (await response.json()) as StreamStartResponse;
  if (response.ok === false || data.success === false) {
    throw new ApiError(data.error || '无法启动工作流', {
      code: data.errorCode,
      status: response.status
    });
  }

  // 验证必要的响应字段
  const sessionId = data.session_id || data.sessionId;
  if (!sessionId || !data.topic) {
    throw new ApiError('工作流启动响应缺少必要字段', {
      code: 'INVALID_RESPONSE',
      status: response.status
    });
  }

  return { ...data, session_id: sessionId, sessionId };
}

export async function getSessionInfo(sessionId: string): Promise<SessionInfoPayload> {
  const response = await fetch('/api/agent/session/' + encodeURIComponent(sessionId));
  if (response.ok === false) {
    throw new ApiError('会话信息获取失败', { status: response.status });
  }
  return (await response.json()) as SessionInfoPayload;
}

export async function getSessionEvents(sessionId: string): Promise<ExecutionEventPayload[]> {
  const response = await fetch('/api/agent-monitoring/sessions/' + encodeURIComponent(sessionId) + '/events');
  if (response.ok === false) {
    throw new ApiError('会话事件获取失败', { status: response.status });
  }
  return (await response.json()) as ExecutionEventPayload[];
}

export async function startSessionSandbox(sessionId: string): Promise<SessionInfoPayload> {
  const response = await fetch('/api/agent/session/' + encodeURIComponent(sessionId) + '/sandbox/start', {
    method: 'POST'
  });
  if (response.ok === false) {
    throw new ApiError('会话沙箱启动失败', { status: response.status });
  }
  return (await response.json()) as SessionInfoPayload;
}

export async function inspectProxyPreview(targetUrl: string): Promise<WebPreviewDiagnosticPayload> {
  const response = await fetch('/api/proxy/inspect?target=' + encodeURIComponent(targetUrl));
  if (response.ok === false) {
    throw new ApiError('网页预览诊断失败', { status: response.status });
  }
  return (await response.json()) as WebPreviewDiagnosticPayload;
}
