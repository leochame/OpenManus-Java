import type {
  ConnectionStatus,
  ExecutionEventPayload,
  ExecutionLogPayload,
  ThoughtStep,
  WorkflowResultPayload
} from '../types/api';
import { extractSearchResults, extractWebUrl, type SearchResultItem } from '../utils/parsers';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  time: string;
  logs: ExecutionLogPayload[];
  thoughtSteps: ThoughtStep[];
}

export interface ToolOutput {
  id: string;
  type: string;
  content: string;
  time: string;
}

export type BrowserStatus =
  | 'idle'
  | 'searching'
  | 'opening-search-page'
  | 'results-ready'
  | 'opening-target-page'
  | 'proxy-rendered'
  | 'proxy-blocked'
  | 'auto-switch-vnc'
  | 'loading'
  | 'proxying'
  | 'rendered'
  | 'blocked'
  | 'fallback-to-vnc'
  | 'open-external';

export interface TimelineEntry {
  id: string;
  type: string;
  title: string;
  description: string;
  time: string;
  url?: string;
  detail?: string;
}

export interface AgentTeamCodingSubTaskView {
  taskId: string;
  title: string;
  goal: string;
  status: string;
  summary: string;
  branchName: string;
  commitSha: string;
  worktreePath: string;
  changedFiles: string[];
  testPassed: boolean | null;
  testSummary: string;
  errorMessage: string;
  ownedPaths: string[];
  verificationCommands: string[];
  conflictRisk: string;
}

export interface AgentTeamCodingIntegrationView {
  success: boolean | null;
  integrationBranch: string;
  mergedBranches: string[];
  conflictFiles: string[];
  verification: string;
  errorMessage: string;
}

export interface AgentTeamCodingView {
  stage: string;
  groupId: string;
  conversationId: string;
  success: boolean | null;
  fallbackToSingleAgent: boolean;
  repositoryPath: string;
  taskCount: number;
  subTasks: AgentTeamCodingSubTaskView[];
  integration: AgentTeamCodingIntegrationView | null;
}

export interface WorkflowState {
  connectionStatus: ConnectionStatus;
  loading: boolean;
  error: string | null;
  sessionId: string | null;
  topic: string | null;
  messages: ChatMessage[];
  searchResults: SearchResultItem[];
  toolOutputs: ToolOutput[];
  executionTimeline: ThoughtStep[];
  currentUrl: string;
  sandboxVncUrl: string | null;
  searchQuery: string;
  searchPageUrl: string;
  browserStatus: BrowserStatus;
  previewMode: 'web' | 'proxy' | 'vnc' | 'external';
  previewBlockedReason: string | null;
  snapshotPath: string | null;
  snapshotPreview: string | null;
  autoSwitchedToVnc: boolean;
  searchTimeline: TimelineEntry[];
  webTimeline: TimelineEntry[];
  agentTeamCoding: AgentTeamCodingView | null;
}

export interface WorkflowSnapshot {
  messages: ChatMessage[];
  searchResults: SearchResultItem[];
  toolOutputs: ToolOutput[];
  currentUrl: string;
  sandboxVncUrl: string | null;
  searchQuery?: string;
  searchPageUrl?: string;
  browserStatus?: BrowserStatus;
  previewMode?: 'web' | 'proxy' | 'vnc' | 'external';
  previewBlockedReason?: string | null;
  snapshotPath?: string | null;
  snapshotPreview?: string | null;
  autoSwitchedToVnc?: boolean;
  searchTimeline?: TimelineEntry[];
  webTimeline?: TimelineEntry[];
  agentTeamCoding?: AgentTeamCodingView | null;
}

export type WorkflowAction =
  | { type: 'SEND_USER_MESSAGE'; payload: { content: string; time: string } }
  | { type: 'START_ASSISTANT_MESSAGE'; payload: { time: string } }
  | { type: 'APPEND_LOG'; payload: ExecutionLogPayload }
  | { type: 'HANDLE_EVENT'; payload: ExecutionEventPayload }
  | { type: 'HANDLE_RESULT'; payload: WorkflowResultPayload }
  | { type: 'SET_STATUS'; payload: ConnectionStatus }
  | { type: 'SET_ERROR'; payload: string | null }
  | { type: 'SET_LOADING'; payload: boolean }
  | { type: 'SET_STREAM'; payload: { sessionId: string; topic: string } }
  | { type: 'SET_VNC_URL'; payload: string | null }
  | { type: 'SET_CURRENT_URL'; payload: string }
  | { type: 'LOAD_SNAPSHOT'; payload: WorkflowSnapshot }
  | { type: 'ROLLBACK_PENDING_ASSISTANT' }
  | { type: 'RESET' };

export const initialWorkflowState: WorkflowState = {
  connectionStatus: 'idle',
  loading: false,
  error: null,
  sessionId: null,
  topic: null,
  messages: [],
  searchResults: [],
  toolOutputs: [],
  executionTimeline: [],
  currentUrl: '',
  sandboxVncUrl: null,
  searchQuery: '',
  searchPageUrl: '',
  browserStatus: 'idle',
  previewMode: 'web',
  previewBlockedReason: null,
  snapshotPath: null,
  snapshotPreview: null,
  autoSwitchedToVnc: false,
  searchTimeline: [],
  webTimeline: [],
  agentTeamCoding: null
};

export function workflowReducer(state: WorkflowState, action: WorkflowAction): WorkflowState {
  switch (action.type) {
    case 'SEND_USER_MESSAGE':
      return {
        ...state,
        messages: state.messages.concat({
          id: randomId(),
          role: 'user',
          content: action.payload.content,
          time: action.payload.time,
          logs: [],
          thoughtSteps: []
        }),
        loading: true,
        error: null
      };
    case 'START_ASSISTANT_MESSAGE':
      return {
        ...state,
        messages: state.messages.concat({
          id: randomId(),
          role: 'assistant',
          content: '',
          time: action.payload.time,
          logs: [],
          thoughtSteps: []
        })
      };
    case 'APPEND_LOG': {
      const messages = cloneMessages(state.messages);
      const lastAssistantIndex = findLastAssistantIndex(messages);
      if (lastAssistantIndex < 0) {
        return {
          ...state,
          messages: [...messages, {
            id: randomId(),
            role: 'assistant',
            content: '',
            time: action.payload.timestamp || formatTime(),
            logs: [action.payload],
            thoughtSteps: []
          }],
          currentUrl: extractWebUrl(action.payload.message || '') || state.currentUrl
        };
      }
      messages[lastAssistantIndex].logs.push(action.payload);

      const foundUrl = extractWebUrl(action.payload.message || '');
      return {
        ...state,
        messages,
        currentUrl: foundUrl || state.currentUrl
      };
    }
    case 'HANDLE_EVENT': {
      const messages = cloneMessages(state.messages);
      const lastAssistantIndex = findLastAssistantIndex(messages);
      const eventType = getEventType(action.payload);
      const metadata = action.payload.metadata || {};
      const outputAsString = normalizePayloadOutput(action.payload.output);
      const nextOutputs = state.toolOutputs.slice();
      const nextExecutionTimeline = state.executionTimeline.slice();
      const fallbackSearchResults = extractSearchResults(outputAsString);
      const fallbackWebSnapshot = parseWebSnapshotOutput(outputAsString);
      const patch: Partial<WorkflowState> = {};
      const thoughtStep = createThoughtStep(action.payload, eventType, outputAsString);

      const keyEventOutput = keyEventToolOutput(action.payload, eventType, outputAsString);
      if (keyEventOutput !== null) {
        nextOutputs.unshift(keyEventOutput);
      } else if (eventType === 'TOOL_CALL_END' && outputAsString.length > 0) {
        nextOutputs.unshift({
          id: randomId(),
          type: action.payload.agent_name || action.payload.agentName || '工具',
          content: outputAsString,
          time: formatTime()
        });
      }

      if (thoughtStep !== null) {
        nextExecutionTimeline.push(thoughtStep);
        if (lastAssistantIndex >= 0) {
          messages[lastAssistantIndex].thoughtSteps.push(thoughtStep);
        }
      }

      applyStructuredBrowserEvent(patch, state, eventType, metadata);
      applyAgentTeamCodingEvent(patch, state, eventType, metadata);

      if (patch.searchResults === undefined && fallbackSearchResults.length > 0) {
        patch.searchResults = fallbackSearchResults;
      }
      if ((patch.currentUrl === undefined || patch.currentUrl.length === 0) && state.currentUrl.length === 0) {
        const fallbackUrl = extractWebUrl(outputAsString);
        if (fallbackUrl) {
          patch.currentUrl = fallbackUrl;
        }
      }
      if (fallbackWebSnapshot) {
        patch.currentUrl = fallbackWebSnapshot.url || patch.currentUrl || state.currentUrl;
        patch.snapshotPath = fallbackWebSnapshot.path || state.snapshotPath;
        patch.snapshotPreview = fallbackWebSnapshot.preview || state.snapshotPreview;
        patch.browserStatus = 'proxy-rendered';
        patch.previewMode = 'proxy';
        patch.previewBlockedReason = null;
        patch.webTimeline = prependTimeline(state.webTimeline, {
          type: 'WEB_FETCH_SNAPSHOT_READY',
          title: '网页快照已生成',
          description: fallbackWebSnapshot.path || '已从工具输出恢复网页快照',
          url: fallbackWebSnapshot.url || undefined
        });
      }

      return {
        ...state,
        messages,
        toolOutputs: nextOutputs,
        executionTimeline: nextExecutionTimeline,
        error: eventType === 'ERROR' ? action.payload.error || '执行异常' : state.error,
        ...patch
      };
    }
    case 'HANDLE_RESULT': {
      const messages = cloneMessages(state.messages);
      const lastAssistantIndex = findLastAssistantIndex(messages);
      const result = action.payload.result || '已完成';
      const resultStep: ThoughtStep = {
        id: randomId(),
        time: formatTimestamp(action.payload.completed_time || action.payload.completedTime),
        kind: 'result',
        title: '执行完成',
        content: result,
        status: action.payload.status || 'SUCCESS',
        agentName: 'execution_coordinator'
      };
      if (lastAssistantIndex >= 0) {
        messages[lastAssistantIndex].content = result;
        messages[lastAssistantIndex].thoughtSteps.push(resultStep);
      }
      return {
        ...state,
        messages,
        executionTimeline: state.executionTimeline.concat(resultStep),
        loading: false,
        connectionStatus: 'idle'
      };
    }
    case 'SET_STATUS':
      return { ...state, connectionStatus: action.payload };
    case 'SET_ERROR':
      return { ...state, error: action.payload };
    case 'SET_LOADING':
      return { ...state, loading: action.payload };
    case 'SET_STREAM':
      return { ...state, sessionId: action.payload.sessionId, topic: action.payload.topic };
    case 'SET_VNC_URL':
      return { ...state, sandboxVncUrl: action.payload };
    case 'SET_CURRENT_URL':
      return { ...state, currentUrl: action.payload };
    case 'LOAD_SNAPSHOT':
      return {
        ...initialWorkflowState,
        messages: normalizeSnapshotMessages(action.payload.messages),
        searchResults: action.payload.searchResults,
        toolOutputs: action.payload.toolOutputs,
        currentUrl: action.payload.currentUrl,
        sandboxVncUrl: action.payload.sandboxVncUrl,
        searchQuery: action.payload.searchQuery || '',
        searchPageUrl: action.payload.searchPageUrl || '',
        browserStatus: action.payload.browserStatus || 'idle',
        previewMode: action.payload.previewMode || 'web',
        previewBlockedReason: action.payload.previewBlockedReason || null,
        snapshotPath: action.payload.snapshotPath || null,
        snapshotPreview: action.payload.snapshotPreview || null,
        autoSwitchedToVnc: action.payload.autoSwitchedToVnc || false,
        searchTimeline: action.payload.searchTimeline || [],
        webTimeline: action.payload.webTimeline || [],
        agentTeamCoding: action.payload.agentTeamCoding || null
      };
    case 'ROLLBACK_PENDING_ASSISTANT': {
      const messages = state.messages.slice();
      const lastMessage = messages[messages.length - 1];
      if (
        lastMessage &&
        lastMessage.role === 'assistant' &&
        lastMessage.content.length === 0 &&
        lastMessage.logs.length === 0 &&
        lastMessage.thoughtSteps.length === 0
      ) {
        messages.pop();
      }
      return {
        ...state,
        messages,
        loading: false
      };
    }
    case 'RESET':
      return { ...initialWorkflowState };
    default:
      return state;
  }
}

function findLastAssistantIndex(messages: ChatMessage[]): number {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    if (messages[i].role === 'assistant') {
      return i;
    }
  }
  return -1;
}

function cloneMessages(messages: ChatMessage[]): ChatMessage[] {
  return messages.map((message) => ({
    ...message,
    logs: message.logs.slice(),
    thoughtSteps: message.thoughtSteps.slice()
  }));
}

function normalizeSnapshotMessages(messages: ChatMessage[]): ChatMessage[] {
  return (messages || []).map((message) => ({
    ...message,
    logs: message.logs || [],
    thoughtSteps: message.thoughtSteps || []
  }));
}

function normalizePayloadOutput(output: unknown): string {
  if (typeof output === 'string') {
    return output;
  }
  if (output === null || output === undefined) {
    return '';
  }
  try {
    return JSON.stringify(output, null, 2);
  } catch {
    return String(output);
  }
}

function getEventType(event: ExecutionEventPayload): string {
  return event.event_type || event.eventType || '';
}

function keyEventToolOutput(event: ExecutionEventPayload,
                            eventType: string,
                            outputAsString: string): ToolOutput | null {
  switch (eventType) {
    case 'AGENT_START':
      if (!event.input) {
        return null;
      }
      return {
        id: randomId(),
        type: '用户请求',
        content: normalizePayloadOutput(event.input),
        time: formatTime()
      };
    case 'LLM_REQUEST':
      return {
        id: randomId(),
        type: 'LLM Request',
        content: outputAsString || normalizePayloadOutput(event.metadata),
        time: formatTime()
      };
    case 'LLM_RESPONSE':
      return {
        id: randomId(),
        type: 'AI Message',
        content: outputAsString || normalizePayloadOutput(event.metadata),
        time: formatTime()
      };
    case 'TOOL_CALL_START':
      return {
        id: randomId(),
        type: `${toolNameOf(event)} 参数`,
        content: normalizePayloadOutput(event.input || event.metadata),
        time: formatTime()
      };
    case 'TOOL_CALL_END':
      return {
        id: randomId(),
        type: `${toolNameOf(event)} 结果`,
        content: outputAsString || normalizePayloadOutput(event.metadata),
        time: formatTime()
      };
    case 'INTERMEDIATE_RESULT': {
      const stage = asString(event.metadata?.stage) || 'STATUS';
      return {
        id: randomId(),
        type: `Agent Team ${stage}`,
        content: outputAsString || normalizePayloadOutput(event.metadata),
        time: formatTime()
      };
    }
    default:
      return null;
  }
}

function toolNameOf(event: ExecutionEventPayload): string {
  const metadataToolName = event.metadata?.toolName;
  if (typeof metadataToolName === 'string' && metadataToolName.length > 0) {
    return metadataToolName;
  }
  return event.agent_name || event.agentName || '工具';
}

function applyStructuredBrowserEvent(patch: Partial<WorkflowState>,
                                     state: WorkflowState,
                                     eventType: string,
                                     metadata: Record<string, unknown>): void {
  const activeUrl = asString(metadata.activeUrl);
  const searchPageUrl = asString(metadata.searchPageUrl);
  const previewMode = normalizePreviewMode(asString(metadata.previewMode));
  const blockReason = firstNonEmpty(asString(metadata.blockReason), asString(metadata.detail));
  const snapshotPath = asString(metadata.snapshotPath);
  const snapshotPreview = asString(metadata.snapshotPreview);
  const sandboxVncUrl = asString(metadata.sandboxVncUrl);
  const autoSwitchedToVnc = Boolean(metadata.autoSwitchedToVnc);
  const query = asString(metadata.query);
  const resultItems = parseSearchResultItems(metadata.resultItems);

  switch (eventType) {
    case 'SEARCH_STARTED':
      patch.searchQuery = query;
      patch.searchPageUrl = searchPageUrl;
      patch.browserStatus = 'searching';
      patch.previewMode = previewMode;
      patch.previewBlockedReason = null;
      patch.autoSwitchedToVnc = false;
      patch.searchTimeline = prependTimeline(state.searchTimeline, {
        type: eventType,
        title: '搜索已发起',
        description: query || '正在准备搜索页面',
        url: searchPageUrl || undefined
      });
      return;
    case 'SEARCH_RESULTS_READY':
      patch.searchResults = resultItems.length > 0 ? resultItems : state.searchResults;
      patch.browserStatus = 'results-ready';
      patch.searchTimeline = prependTimeline(state.searchTimeline, {
        type: eventType,
        title: '搜索结果已返回',
        description: resultItems.length > 0 ? `共 ${resultItems.length} 条结构化结果` : '结果已返回，可打开搜索页继续查看',
        url: searchPageUrl || state.searchPageUrl || undefined,
        detail: blockReason || undefined
      });
      return;
    case 'BROWSER_URL_OPENED':
      patch.currentUrl = activeUrl || state.currentUrl;
      patch.sandboxVncUrl = sandboxVncUrl || state.sandboxVncUrl;
      patch.browserStatus = 'opening-target-page';
      patch.previewMode = previewMode;
      patch.previewBlockedReason = null;
      patch.autoSwitchedToVnc = false;
      patch.webTimeline = prependTimeline(state.webTimeline, {
        type: eventType,
        title: '网页已打开',
        description: activeUrl || '浏览器已切换到目标页',
        url: activeUrl || undefined
      });
      return;
    case 'WEB_FETCH_STARTED':
      patch.currentUrl = activeUrl || state.currentUrl;
      patch.browserStatus = previewMode === 'proxy' ? 'proxying' : 'loading';
      patch.previewMode = previewMode;
      patch.previewBlockedReason = null;
      patch.webTimeline = prependTimeline(state.webTimeline, {
        type: eventType,
        title: '网页抓取中',
        description: activeUrl || '正在抓取网页内容',
        url: activeUrl || undefined
      });
      return;
    case 'WEB_FETCH_SNAPSHOT_READY':
      patch.currentUrl = activeUrl || state.currentUrl;
      patch.snapshotPath = snapshotPath || state.snapshotPath;
      patch.snapshotPreview = snapshotPreview || state.snapshotPreview;
      patch.browserStatus = 'proxy-rendered';
      patch.previewMode = previewMode;
      patch.webTimeline = prependTimeline(state.webTimeline, {
        type: eventType,
        title: '网页快照已生成',
        description: snapshotPath || '快照已生成',
        url: activeUrl || undefined
      });
      return;
    case 'WEB_PREVIEW_BLOCKED':
      patch.currentUrl = activeUrl || state.currentUrl;
      patch.browserStatus = state.sandboxVncUrl ? 'auto-switch-vnc' : 'proxy-blocked';
      patch.previewMode = previewMode;
      patch.previewBlockedReason = blockReason || '正在切换真实浏览器';
      patch.autoSwitchedToVnc = autoSwitchedToVnc || state.sandboxVncUrl !== null;
      patch.webTimeline = prependTimeline(state.webTimeline, {
        type: eventType,
        title: '切换真实浏览器',
        description: blockReason || '代理预览不可作为最终展示，改用真实浏览器',
        url: activeUrl || undefined
      });
      return;
    case 'VNC_READY':
      patch.sandboxVncUrl = asString(metadata.sandboxVncUrl) || state.sandboxVncUrl;
      patch.webTimeline = prependTimeline(state.webTimeline, {
        type: eventType,
        title: 'VNC 已就绪',
        description: '可切换到 VNC 继续查看网页或执行过程'
      });
      return;
    default:
  }
}

function applyAgentTeamCodingEvent(
  patch: Partial<WorkflowState>,
  state: WorkflowState,
  eventType: string,
  metadata: Record<string, unknown>
): void {
  if (eventType !== 'INTERMEDIATE_RESULT') {
    return;
  }
  const stage = asString(metadata.stage);
  const current = state.agentTeamCoding || createEmptyAgentTeamCodingView();
  const next: AgentTeamCodingView = {
    ...current,
    stage: stage || current.stage,
    groupId: asString(metadata.groupId) || current.groupId,
    conversationId: asString(metadata.conversationId) || current.conversationId,
    success: asNullableBoolean(metadata.success, current.success),
    fallbackToSingleAgent: asBoolean(metadata.fallbackToSingleAgent, current.fallbackToSingleAgent),
    repositoryPath: asString(metadata.repositoryPath) || current.repositoryPath,
    taskCount: asNumber(metadata.taskCount, current.taskCount),
    subTasks: mergeAgentTeamCodingSubTasks(current.subTasks, metadata),
    integration: mergeAgentTeamCodingIntegration(current.integration, metadata)
  };
  patch.agentTeamCoding = next;
}

function prependTimeline(items: TimelineEntry[], item: Omit<TimelineEntry, 'id' | 'time'>): TimelineEntry[] {
  return [{
    id: randomId(),
    time: formatTime(),
    ...item
  }, ...items].slice(0, 30);
}

function parseSearchResultItems(value: unknown): SearchResultItem[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => {
      if (item === null || typeof item !== 'object') {
        return null;
      }
      const record = item as Record<string, unknown>;
      const title = asString(record.title);
      const url = asString(record.url);
      const snippet = asString(record.snippet);
      if (title.length === 0 || url.length === 0) {
        return null;
      }
      return { title, url, snippet };
    })
    .filter((item): item is SearchResultItem => item !== null);
}

function createEmptyAgentTeamCodingView(): AgentTeamCodingView {
  return {
    stage: '',
    groupId: '',
    conversationId: '',
    success: null,
    fallbackToSingleAgent: false,
    repositoryPath: '',
    taskCount: 0,
    subTasks: [],
    integration: null
  };
}

function mergeAgentTeamCodingSubTasks(
  current: AgentTeamCodingSubTaskView[],
  metadata: Record<string, unknown>
): AgentTeamCodingSubTaskView[] {
  const taskDefinitions = parseTaskDefinitions(metadata.tasks);
  const subAgentEntries = parseSubAgentEntries(metadata.subAgents);
  if (taskDefinitions.length === 0 && subAgentEntries.length === 0) {
    return current;
  }

  const byTaskId = new Map<string, AgentTeamCodingSubTaskView>();
  for (const item of current) {
    byTaskId.set(item.taskId, item);
  }
  for (const task of taskDefinitions) {
    const existing = byTaskId.get(task.taskId) || emptySubTask(task.taskId);
    byTaskId.set(task.taskId, {
      ...existing,
      title: task.title || existing.title,
      goal: task.goal || existing.goal,
      ownedPaths: task.ownedPaths.length > 0 ? task.ownedPaths : existing.ownedPaths,
      verificationCommands: task.verificationCommands.length > 0 ? task.verificationCommands : existing.verificationCommands,
      conflictRisk: task.conflictRisk || existing.conflictRisk
    });
  }
  for (const subAgent of subAgentEntries) {
    const existing = byTaskId.get(subAgent.taskId) || emptySubTask(subAgent.taskId);
    byTaskId.set(subAgent.taskId, {
      ...existing,
      status: subAgent.status || existing.status,
      summary: subAgent.summary || existing.summary,
      branchName: subAgent.branchName || existing.branchName,
      commitSha: subAgent.commitSha || existing.commitSha,
      worktreePath: subAgent.worktreePath || existing.worktreePath,
      changedFiles: subAgent.changedFiles.length > 0 ? subAgent.changedFiles : existing.changedFiles,
      testPassed: subAgent.testPassed,
      testSummary: subAgent.testSummary || existing.testSummary,
      errorMessage: subAgent.errorMessage || existing.errorMessage
    });
  }
  return Array.from(byTaskId.values()).sort((a, b) => a.taskId.localeCompare(b.taskId));
}

function mergeAgentTeamCodingIntegration(
  current: AgentTeamCodingIntegrationView | null,
  metadata: Record<string, unknown>
): AgentTeamCodingIntegrationView | null {
  const hasIntegrationFields =
    metadata.integrationBranch !== undefined ||
    metadata.verification !== undefined ||
    metadata.conflictFiles !== undefined ||
    metadata.integrationSuccess !== undefined ||
    metadata.errorMessage !== undefined ||
    metadata.mergedBranches !== undefined;
  if (!hasIntegrationFields) {
    return current;
  }
  return {
    success: asNullableBoolean(metadata.integrationSuccess, current?.success ?? null),
    integrationBranch: asString(metadata.integrationBranch) || current?.integrationBranch || '',
    mergedBranches: asStringArray(metadata.mergedBranches, current?.mergedBranches || []),
    conflictFiles: asStringArray(metadata.conflictFiles, current?.conflictFiles || []),
    verification: asString(metadata.verification) || current?.verification || '',
    errorMessage: asString(metadata.errorMessage) || current?.errorMessage || ''
  };
}

function parseTaskDefinitions(value: unknown): Array<{
  taskId: string;
  title: string;
  goal: string;
  ownedPaths: string[];
  verificationCommands: string[];
  conflictRisk: string;
}> {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => {
      if (item === null || typeof item !== 'object') {
        return null;
      }
      const record = item as Record<string, unknown>;
      const taskId = asString(record.taskId);
      if (taskId.length === 0) {
        return null;
      }
      return {
        taskId,
        title: asString(record.title),
        goal: asString(record.goal),
        ownedPaths: asStringArray(record.ownedPaths),
        verificationCommands: asStringArray(record.verificationCommands),
        conflictRisk: asString(record.conflictRisk)
      };
    })
    .filter((item): item is {
      taskId: string;
      title: string;
      goal: string;
      ownedPaths: string[];
      verificationCommands: string[];
      conflictRisk: string;
    } => item !== null);
}

function parseSubAgentEntries(value: unknown): Array<{
  taskId: string;
  status: string;
  summary: string;
  branchName: string;
  commitSha: string;
  worktreePath: string;
  changedFiles: string[];
  testPassed: boolean | null;
  testSummary: string;
  errorMessage: string;
}> {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => {
      if (item === null || typeof item !== 'object') {
        return null;
      }
      const record = item as Record<string, unknown>;
      const taskId = asString(record.taskId);
      if (taskId.length === 0) {
        return null;
      }
      return {
        taskId,
        status: asString(record.status),
        summary: asString(record.summary),
        branchName: asString(record.branchName),
        commitSha: asString(record.commitSha),
        worktreePath: asString(record.worktreePath),
        changedFiles: asStringArray(record.changedFiles),
        testPassed: asNullableBoolean(record.testPassed, null),
        testSummary: asString(record.testSummary),
        errorMessage: asString(record.errorMessage)
      };
    })
    .filter((item): item is {
      taskId: string;
      status: string;
      summary: string;
      branchName: string;
      commitSha: string;
      worktreePath: string;
      changedFiles: string[];
      testPassed: boolean | null;
      testSummary: string;
      errorMessage: string;
    } => item !== null);
}

function emptySubTask(taskId: string): AgentTeamCodingSubTaskView {
  return {
    taskId,
    title: '',
    goal: '',
    status: '',
    summary: '',
    branchName: '',
    commitSha: '',
    worktreePath: '',
    changedFiles: [],
    testPassed: null,
    testSummary: '',
    errorMessage: '',
    ownedPaths: [],
    verificationCommands: [],
    conflictRisk: ''
  };
}

function parseWebSnapshotOutput(output: string): { url: string; path: string; preview: string } | null {
  if (output.length === 0 || output.trim().startsWith('{') === false) {
    return null;
  }
  try {
    const parsed = JSON.parse(output) as Record<string, unknown>;
    const url = asString(parsed.url);
    const path = asString(parsed.path);
    const preview = asString(parsed.preview);
    if (url.length === 0 || preview.length === 0) {
      return null;
    }
    return { url, path, preview };
  } catch {
    return null;
  }
}

function asString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function asStringArray(value: unknown, fallback: string[] = []): string[] {
  if (!Array.isArray(value)) {
    return fallback;
  }
  return value.filter((item): item is string => typeof item === 'string' && item.length > 0);
}

function asNumber(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function asBoolean(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback;
}

function asNullableBoolean(value: unknown, fallback: boolean | null): boolean | null {
  return typeof value === 'boolean' ? value : fallback;
}

function firstNonEmpty(...values: string[]): string {
  return values.find((value) => value.length > 0) || '';
}

function normalizePreviewMode(value: string): 'web' | 'proxy' | 'vnc' | 'external' {
  if (value === 'proxy' || value === 'vnc' || value === 'external') {
    return value;
  }
  return 'web';
}

function createThoughtStep(
  payload: ExecutionEventPayload,
  eventType: string,
  outputAsString: string
): ThoughtStep | null {
  const agentName = payload.agentName || payload.agent_name || 'system';
  const status = payload.status || '';
  const time = formatTimestamp(payload.end_time || payload.endTime || payload.start_time || payload.startTime);
  const input = summarize(normalizePayloadOutput(payload.input));
  const output = summarize(outputAsString);

  if (eventType === 'LLM_REQUEST') {
    return {
      id: randomId(),
      time,
      kind: 'llm_request',
      title: '模型思考中',
      content: summarizeMetadata(payload.metadata, ['iteration', 'messageCount', 'toolCount']),
      status: status || 'RUNNING',
      agentName
    };
  }
  if (eventType === 'LLM_RESPONSE') {
    return {
      id: randomId(),
      time,
      kind: 'llm_response',
      title: '模型已响应',
      content: output || summarizeMetadata(payload.metadata, ['finishReason', 'toolCallCount']),
      status: status || 'SUCCESS',
      agentName
    };
  }
  if (eventType === 'TOOL_CALL_START') {
    return {
      id: randomId(),
      time,
      kind: 'tool_start',
      title: `调用工具: ${toolNameOf(payload)}`,
      content: input || summarizeMetadata(payload.metadata, ['toolName', 'toolCallId']),
      status: status || 'RUNNING',
      agentName
    };
  }
  if (eventType === 'TOOL_CALL_END') {
    return {
      id: randomId(),
      time,
      kind: 'tool_end',
      title: `工具完成: ${toolNameOf(payload)}`,
      content: output || summarizeMetadata(payload.metadata, ['toolName', 'toolCallId']),
      status: status || 'SUCCESS',
      agentName
    };
  }
  if (eventType === 'ERROR') {
    return {
      id: randomId(),
      time,
      kind: 'error',
      title: '执行异常',
      content: payload.error || output || '执行异常',
      status: status || 'ERROR',
      agentName
    };
  }
  if (eventType === 'INTERMEDIATE_RESULT') {
    const stage = asString(payload.metadata?.stage) || 'STATUS';
    return {
      id: randomId(),
      time,
      kind: 'status',
      title: `Agent Team ${stage}`,
      content: output || summarizeMetadata(payload.metadata, ['stage', 'integrationBranch', 'verification']),
      status: status || 'RUNNING',
      agentName
    };
  }
  if (eventType === 'AGENT_START' && hasIteration(payload.metadata)) {
    return {
      id: randomId(),
      time,
      kind: 'iteration',
      title: `执行迭代 #${String(payload.metadata?.iteration ?? '')}`,
      content: summarizeMetadata(payload.metadata, ['taskPlan', 'currentTool']),
      status: status || 'RUNNING',
      agentName
    };
  }
  return null;
}

function hasIteration(metadata: Record<string, unknown> | undefined): boolean {
  return typeof metadata?.iteration === 'number' || typeof metadata?.iteration === 'string';
}

function summarizeMetadata(metadata: Record<string, unknown> | undefined, keys: string[]): string {
  if (!metadata) {
    return '';
  }
  return keys
    .map((key) => {
      const value = metadata[key];
      if (value === null || value === undefined || value === '') {
        return null;
      }
      return `${key}: ${summarize(String(value), 140)}`;
    })
    .filter((line): line is string => line !== null)
    .join('\n');
}

function summarize(content: string, maxLength = 240): string {
  const normalized = content.trim();
  if (normalized.length <= maxLength) {
    return normalized;
  }
  return normalized.slice(0, maxLength) + '...';
}

function formatTimestamp(input?: string): string {
  if (!input) {
    return formatTime();
  }
  const date = new Date(input);
  if (Number.isNaN(date.getTime())) {
    return input;
  }
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function randomId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'id-' + Date.now() + '-' + Math.random();
}

function formatTime(): string {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}
