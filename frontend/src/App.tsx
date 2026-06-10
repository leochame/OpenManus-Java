import { FormEvent, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { marked } from 'marked';
import {
  ApiError,
  getSessionInfo,
  inspectProxyPreview,
  startSessionSandbox,
  startWorkflow
} from './api/agentApi';
import type { ThoughtStep } from './types/api';
import {
  initialWorkflowState,
  workflowReducer,
  type AgentTeamCodingSubTaskView,
  type AgentTeamCodingView,
  type BrowserStatus,
  type ChatMessage,
  type TimelineEntry,
  type ToolOutput
} from './store/workflowStore';
import { WorkflowSocketClient } from './ws/workflowSocketClient';

marked.setOptions({ breaks: true, gfm: true });

export default function App(): JSX.Element {
  const [state, dispatch] = useReducer(workflowReducer, initialWorkflowState);
  const [input, setInput] = useState('');
  const [targetRepositoryPath, setTargetRepositoryPath] = useState('');
  const [browserMode, setBrowserMode] = useState<'web' | 'snapshot' | 'vnc'>('web');
  const [useAgentTeam, setUseAgentTeam] = useState(true);
  const [useAgentTeamCoding, setUseAgentTeamCoding] = useState(false);
  const [useProxy, setUseProxy] = useState(true);
  const [showToolPanel, setShowToolPanel] = useState(true);
  const [activeToolTab, setActiveToolTab] = useState<'search' | 'status' | 'output'>('search');
  const [iframeBlockedReason, setIframeBlockedReason] = useState<string | null>(null);
  const socketRef = useRef<WorkflowSocketClient | null>(null);

  const statusText = useMemo(() => {
    switch (state.connectionStatus) {
      case 'connecting':
        return 'Connecting';
      case 'connected':
        return 'Connected';
      case 'error':
        return 'Connection Error';
      default:
        return 'Idle';
    }
  }, [state.connectionStatus]);

  const connectionTone = state.connectionStatus;

  useEffect(() => {
    return () => {
      void disconnectSocket();
    };
  }, []);

  useEffect(() => {
    if ((state.browserStatus === 'auto-switch-vnc' || state.previewMode === 'vnc') && state.sandboxVncUrl) {
      setBrowserMode('vnc');
    }
  }, [state.browserStatus, state.previewMode, state.sandboxVncUrl]);

  useEffect(() => {
    if (state.snapshotPreview && (state.browserStatus === 'proxy-rendered' || state.previewBlockedReason)) {
      setBrowserMode('snapshot');
    }
  }, [state.browserStatus, state.previewBlockedReason, state.snapshotPreview]);

  useEffect(() => {
    if (state.currentUrl.length === 0 || useProxy === false) {
      return;
    }
    if (state.previewMode === 'vnc' && state.sandboxVncUrl) {
      return;
    }
    let cancelled = false;
    void inspectCurrentUrl(() => cancelled);
    return () => {
      cancelled = true;
    };
  }, [state.currentUrl, useProxy, state.sandboxVncUrl]);

  async function sendMessage(event?: FormEvent): Promise<void> {
    if (event) {
      event.preventDefault();
    }
    const content = input.trim();
    if (content.length === 0 || state.loading) {
      return;
    }

    dispatch({ type: 'SEND_USER_MESSAGE', payload: { content, time: nowTime() } });
    dispatch({ type: 'START_ASSISTANT_MESSAGE', payload: { time: nowTime() } });
    setInput('');

    try {
      await disconnectSocket();
      dispatch({ type: 'SET_STATUS', payload: 'connecting' });

      socketRef.current = new WorkflowSocketClient();
      await socketRef.current.connect();

      const startData = await startWorkflow({
        input: content,
        sessionId: state.sessionId || undefined,
        agentTeam: useAgentTeam,
        agentTeamCoding: useAgentTeamCoding,
        targetRepositoryPath: useAgentTeamCoding ? targetRepositoryPath.trim() || undefined : undefined
      });
      const sessionId = startData.session_id || startData.sessionId || '';
      const topic = startData.topic || '';
      if (sessionId.length === 0 || topic.length === 0) {
        throw new ApiError('Backend did not return a subscribable topic');
      }

      dispatch({ type: 'SET_STREAM', payload: { sessionId, topic } });
      dispatch({ type: 'SET_STATUS', payload: 'connected' });
      void bootstrapSandbox(sessionId);

      socketRef.current.subscribe(topic, {
        onLog: (payload) => {
          const payloadSessionId = payload.sessionId || payload.session_id;
          if (sessionId.length > 0 && payloadSessionId && payloadSessionId !== sessionId) {
            return;
          }
          dispatch({ type: 'APPEND_LOG', payload });
        },
        onEvent: (payload) => {
          const payloadSessionId = payload.sessionId || payload.session_id;
          if (sessionId.length > 0 && payloadSessionId && payloadSessionId !== sessionId) {
            return;
          }
          dispatch({ type: 'HANDLE_EVENT', payload });
        },
        onResult: async (payload) => {
          const payloadSessionId = payload.sessionId || payload.session_id;
          if (sessionId.length > 0 && payloadSessionId && payloadSessionId !== sessionId) {
            return;
          }
          await completeExecutionWithResult(sessionId, payload.result || '已完成');
        },
        onSocketError: (message) => {
          dispatch({ type: 'SET_ERROR', payload: message });
          dispatch({ type: 'SET_LOADING', payload: false });
          dispatch({ type: 'SET_STATUS', payload: 'error' });
        }
      });
    } catch (error) {
      const message =
        error instanceof ApiError && error.code === 'SESSION_BUSY'
          ? '当前会话仍在处理中，请稍后再试'
          : error instanceof Error
            ? error.message
            : 'Send failed';
      dispatch({ type: 'ROLLBACK_PENDING_ASSISTANT' });
      dispatch({ type: 'SET_ERROR', payload: message });
      dispatch({ type: 'SET_STATUS', payload: 'error' });
      await disconnectSocket();
    }
  }

  async function pollSandbox(sessionId: string): Promise<void> {
    for (let i = 0; i < 20; i += 1) {
      try {
        const sessionData = await getSessionInfo(sessionId);
        if (sessionData.sandboxAvailable && sessionData.sandboxVncUrl) {
          dispatch({ type: 'SET_VNC_URL', payload: sessionData.sandboxVncUrl });
          return;
        }
      } catch {
        // Ignore intermittent polling failure.
      }
      await sleep(3000);
    }
  }

  async function bootstrapSandbox(sessionId: string): Promise<void> {
    try {
      const sessionData = await startSessionSandbox(sessionId);
      if (sessionData.sandboxAvailable && sessionData.sandboxVncUrl) {
        dispatch({ type: 'SET_VNC_URL', payload: sessionData.sandboxVncUrl });
        return;
      }
    } catch {
      // Ignore bootstrap failure and fall back to polling.
    }
    await pollSandbox(sessionId);
  }

  async function inspectCurrentUrl(isCancelled: () => boolean): Promise<void> {
    try {
      const diagnostic = await inspectProxyPreview(state.currentUrl);
      if (isCancelled()) {
        return;
      }
      if (diagnostic.previewableHtml) {
        setIframeBlockedReason(null);
        return;
      }
      dispatch({
        type: 'HANDLE_EVENT',
        payload: {
          eventType: 'WEB_PREVIEW_BLOCKED',
          metadata: {
            activeUrl: diagnostic.targetUrl,
            previewMode: diagnostic.previewMode,
            blockReason: diagnostic.reasonCode || diagnostic.state,
            detail: diagnostic.reason,
            autoSwitchedToVnc: diagnostic.fallbackToVnc
          }
        }
      });
      setIframeBlockedReason(diagnostic.reason || diagnostic.reasonCode || '正在切换真实浏览器');
      if (state.snapshotPreview) {
        setBrowserMode('snapshot');
      } else if (diagnostic.fallbackToVnc && state.sandboxVncUrl) {
        setBrowserMode('vnc');
      }
    } catch (error) {
      if (isCancelled()) {
        return;
      }
      setIframeBlockedReason(error instanceof Error ? error.message : '网页预览诊断失败');
    }
  }

  async function completeExecutionWithResult(sessionId: string, result: string): Promise<void> {
    dispatch({
      type: 'HANDLE_RESULT',
      payload: {
        sessionId,
        result
      }
    });
    await pollSandbox(sessionId);
    await disconnectSocket();
  }

  async function disconnectSocket(): Promise<void> {
    if (socketRef.current !== null) {
      await socketRef.current.disconnect();
      socketRef.current = null;
    }
  }

  async function reconnect(): Promise<void> {
    if (state.topic === null || state.topic.length === 0) {
      dispatch({ type: 'SET_ERROR', payload: 'No reconnectable session found' });
      return;
    }
    try {
      await disconnectSocket();
      dispatch({ type: 'SET_STATUS', payload: 'connecting' });
      socketRef.current = new WorkflowSocketClient();
      await socketRef.current.connect();
      dispatch({ type: 'SET_STATUS', payload: 'connected' });
      socketRef.current.subscribe(state.topic, {
        onLog: (payload) => {
          const payloadSessionId = payload.sessionId || payload.session_id;
          if (state.sessionId && payloadSessionId && payloadSessionId !== state.sessionId) {
            return;
          }
          dispatch({ type: 'APPEND_LOG', payload });
        },
        onEvent: (payload) => {
          const payloadSessionId = payload.sessionId || payload.session_id;
          if (state.sessionId && payloadSessionId && payloadSessionId !== state.sessionId) {
            return;
          }
          dispatch({ type: 'HANDLE_EVENT', payload });
        },
        onResult: async (payload) => {
          const payloadSessionId = payload.sessionId || payload.session_id;
          if (state.sessionId && payloadSessionId && payloadSessionId !== state.sessionId) {
            return;
          }
          if (state.sessionId !== null) {
            await completeExecutionWithResult(state.sessionId, payload.result || '已完成');
          }
        },
        onSocketError: (message) => {
          dispatch({ type: 'SET_ERROR', payload: message });
          dispatch({ type: 'SET_LOADING', payload: false });
          dispatch({ type: 'SET_STATUS', payload: 'error' });
        }
      });
    } catch (error) {
      dispatch({ type: 'SET_STATUS', payload: 'error' });
      dispatch({ type: 'SET_ERROR', payload: error instanceof Error ? error.message : 'Reconnection failed' });
    }
  }

  async function stopWatchingStream(): Promise<void> {
    await disconnectSocket();
    dispatch({ type: 'SET_LOADING', payload: false });
    dispatch({ type: 'SET_STATUS', payload: 'idle' });
    dispatch({
      type: 'SET_ERROR',
      payload: '已停止实时订阅。后台任务可能仍在执行，完成前同一会话的新请求可能返回忙碌。'
    });
  }

  function resetConversation(): void {
    dispatch({ type: 'RESET' });
    setIframeBlockedReason(null);
    void disconnectSocket();
  }

  function onUrlChange(url: string): void {
    dispatch({ type: 'SET_CURRENT_URL', payload: url });
  }

  return (
    <div className="app-shell">
      <header className="top-nav">
        <div className="brand-block">
          <div className="brand">OpenManus</div>
          <div className="brand-meta">Agent workspace</div>
        </div>
        <div className="nav-actions">
          <span className={'status-tag status-' + connectionTone}>
            <span className="status-light" aria-hidden="true" />
            {statusText}
          </span>
          <button className="btn secondary" onClick={() => void reconnect()}>
            Reconnect
          </button>
          <button className="btn secondary" onClick={() => void stopWatchingStream()} disabled={state.loading === false}>
            Stop Watching
          </button>
          <button className="btn" onClick={resetConversation}>
            New Chat
          </button>
        </div>
      </header>

      <main className="workspace">
        <section className="panel chat-panel">
          <div className="panel-title">
            <span>Conversation</span>
          </div>
          <div className="messages">
            {state.messages.length === 0 ? <div className="empty">Type a prompt to start the conversation</div> : null}
            {state.messages.map((message) => (
              <MessageCard
                key={message.id}
                message={message}
                isStreaming={state.loading && isLatestAssistantMessage(state.messages, message.id)}
              />
            ))}
            {state.error ? <div className="error-banner">{state.error}</div> : null}
          </div>
          <form className="input-form" onSubmit={(event) => void sendMessage(event)}>
            <input
              value={targetRepositoryPath}
              onChange={(event) => setTargetRepositoryPath(event.target.value)}
              placeholder="Target repository path for Team Coding (optional)"
              disabled={state.loading}
            />
            <textarea
              rows={3}
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={(event) => {
                if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                  event.preventDefault();
                  void sendMessage();
                }
              }}
              placeholder="Type a message, Ctrl/⌘+Enter to send"
            />
            <div className="input-actions">
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={useAgentTeam}
                  onChange={(e) => setUseAgentTeam(e.target.checked)}
                  disabled={state.loading}
                />
                Agent Team
              </label>
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={useAgentTeamCoding}
                  onChange={(e) => setUseAgentTeamCoding(e.target.checked)}
                  disabled={state.loading}
                />
                Team Coding
              </label>
              <button
                className="btn secondary"
                type="button"
                onClick={() => setInput('')}
                disabled={state.loading}
              >
                Clear
              </button>
              <button className="btn" type="submit" disabled={state.loading}>
                {state.loading ? 'Processing...' : 'Send'}
              </button>
            </div>
          </form>
        </section>

        <section className={'panel tool-panel ' + (showToolPanel ? '' : 'collapsed')}>
          <div className="panel-title with-action">
            <span>Tools</span>
            <button className="btn tiny secondary" onClick={() => setShowToolPanel(!showToolPanel)}>
              {showToolPanel ? 'Hide' : 'Show'}
            </button>
          </div>
          <div className="tool-tabs">
            <button
              className={activeToolTab === 'search' ? 'active' : ''}
              onClick={() => setActiveToolTab('search')}
            >
              Search Trail
            </button>
            <button
              className={activeToolTab === 'status' ? 'active' : ''}
              onClick={() => setActiveToolTab('status')}
            >
              Web Status
            </button>
            <button
              className={activeToolTab === 'output' ? 'active' : ''}
              onClick={() => setActiveToolTab('output')}
            >
              Tool Output
            </button>
          </div>
          <div className="tool-body">
            {activeToolTab === 'search' ? (
              <>
                <TimelineSection title="Search Timeline" items={state.searchTimeline} />
                {state.searchResults.map((item) => (
                  <article key={item.url} className="card">
                    <h4>{item.title}</h4>
                    <a href={item.url} target="_blank" rel="noreferrer">
                      {item.url}
                    </a>
                    <p>{item.snippet}</p>
                  </article>
                ))}
              </>
            ) : null}
            {activeToolTab === 'status' ? (
              <>
                <article className="card">
                  <h4>Browser State</h4>
                  <p className="muted">Status: {statusLabel(state.browserStatus, state.previewMode)}</p>
                  <p className="muted">Preview: {state.previewMode}</p>
                  {state.previewBlockedReason || iframeBlockedReason ? (
                    <p>{state.previewBlockedReason || iframeBlockedReason}</p>
                  ) : null}
                  {state.snapshotPath ? <p className="muted">Snapshot: {state.snapshotPath}</p> : null}
                  {state.snapshotPreview ? <pre>{state.snapshotPreview}</pre> : null}
                </article>
                {state.agentTeamCoding ? <AgentTeamCodingCard view={state.agentTeamCoding} /> : null}
                <TimelineSection title="Web Timeline" items={state.webTimeline} />
              </>
            ) : null}
            {activeToolTab === 'output'
              ? state.toolOutputs.map((output) => <OutputCard key={output.id} output={output} />)
              : null}
            {activeToolTab === 'search' && state.searchResults.length === 0 ? (
              <div className="empty">No search events yet</div>
            ) : null}
            {activeToolTab === 'status' && state.webTimeline.length === 0 ? (
              <div className="empty">No web status yet</div>
            ) : null}
            {activeToolTab === 'output' && state.toolOutputs.length === 0 ? (
              <div className="empty">No tool output yet</div>
            ) : null}
          </div>
        </section>

        <section className="panel browser-panel">
          <div className="panel-title">
            <span>Browser / Sandbox</span>
          </div>
          <div className="browser-toolbar">
            <input
              value={state.currentUrl}
              onChange={(event) => onUrlChange(event.target.value)}
              onBlur={() => onUrlChange(normalizeUrl(state.currentUrl))}
              placeholder="Enter URL"
            />
            <label className="toggle">
              <input
                type="checkbox"
                checked={useProxy}
                onChange={(event) => setUseProxy(event.target.checked)}
              />
              Proxy
            </label>
            <button className={browserMode === 'web' ? 'active' : ''} onClick={() => setBrowserMode('web')}>
              Web
            </button>
            <button
              className={browserMode === 'snapshot' ? 'active' : ''}
              onClick={() => setBrowserMode('snapshot')}
              disabled={!state.snapshotPreview}
            >
              Snapshot
            </button>
            <button
              className={browserMode === 'vnc' ? 'active' : ''}
              onClick={() => setBrowserMode('vnc')}
              disabled={state.sandboxVncUrl === null}
            >
              VNC
            </button>
            {state.currentUrl ? (
              <a className="btn tiny secondary" href={state.currentUrl} target="_blank" rel="noreferrer">
                Open External
              </a>
            ) : null}
          </div>
          <div className="panel-title with-action">
            <span className={'browser-status status-' + browserStatusTone(state.browserStatus)}>
              <span className="status-light" aria-hidden="true" />
              {statusLabel(state.browserStatus, state.previewMode)}
            </span>
            <span>
              {state.previewMode === 'vnc' && state.sandboxVncUrl
                ? '真实浏览器正在展示该网页'
                : state.currentUrl || iframeBlockedReason || '等待网页事件'}
            </span>
          </div>
          <div className="browser-frame">
            {browserMode === 'web' ? (
              state.currentUrl.length > 0 ? (
                <iframe
                  src={proxyUrl(state.currentUrl, useProxy)}
                  title="web-preview"
                  onLoad={() => {
                    if (state.previewBlockedReason === null) {
                      setIframeBlockedReason(null);
                    }
                  }}
                  onError={() => {
                    const reason = '当前站点可能拒绝 iframe 嵌入，或代理后的子资源加载失败';
                    setIframeBlockedReason(reason);
                    dispatch({
                      type: 'HANDLE_EVENT',
                      payload: {
                        eventType: 'WEB_PREVIEW_BLOCKED',
                        metadata: {
                          activeUrl: state.currentUrl,
                          previewMode: state.sandboxVncUrl ? 'vnc' : 'external',
                          blockReason: 'iframe-blocked',
                          detail: reason,
                          autoSwitchedToVnc: state.sandboxVncUrl !== null
                        }
                      }
                    });
                    if (state.snapshotPreview) {
                      setBrowserMode('snapshot');
                    } else if (state.sandboxVncUrl) {
                      setBrowserMode('vnc');
                    }
                  }}
                />
              ) : (
                <div className="empty">Enter a URL to start browsing</div>
              )
            ) : browserMode === 'snapshot' ? (
              state.snapshotPreview ? (
                <iframe
                  srcDoc={buildSnapshotDocument(state.snapshotPreview, state.currentUrl)}
                  title="snapshot-preview"
                  sandbox="allow-forms allow-popups allow-popups-to-escape-sandbox allow-same-origin"
                />
              ) : (
                <div className="empty">No page snapshot yet</div>
              )
            ) : state.sandboxVncUrl ? (
              <iframe src={state.sandboxVncUrl} title="vnc-preview" />
            ) : (
              <div className="empty">正在启动真实浏览器工作区...</div>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}

function TimelineSection({ title, items }: { title: string; items: TimelineEntry[] }): JSX.Element {
  return (
    <>
      <article className="card">
        <h4>{title}</h4>
        <p className="muted">{items.length > 0 ? `Latest ${items.length} events` : 'Waiting for events'}</p>
      </article>
      {items.map((item) => (
        <article key={item.id} className="card">
          <h4>{item.title}</h4>
          <p className="muted">{item.time}</p>
          <p>{item.description}</p>
          {item.url ? (
            <a href={item.url} target="_blank" rel="noreferrer">
              {item.url}
            </a>
          ) : null}
        </article>
      ))}
    </>
  );
}

function MessageCard({ message, isStreaming }: { message: ChatMessage; isStreaming: boolean }): JSX.Element {
  return (
    <article className={'message-card ' + message.role}>
      <header>
        <strong>{message.role === 'user' ? 'User' : 'AI'}</strong>
        <span>{message.time}</span>
      </header>
      <div
        className="markdown"
        dangerouslySetInnerHTML={{ __html: marked.parse(message.content || '') as string }}
      />
      {message.role === 'assistant' ? (
        <section className="thought-panel">
          <div className="thought-panel-header">
            <strong>Execution Trace</strong>
            {isStreaming ? <span className="thought-status">Streaming</span> : null}
          </div>
          {message.thoughtSteps.length > 0 ? (
            <div className="thought-steps">
              {message.thoughtSteps.map((step) => (
                <ThoughtStepCard key={step.id} step={step} />
              ))}
            </div>
          ) : isStreaming ? (
            <div className="thought-placeholder">Waiting for backend events...</div>
          ) : null}
        </section>
      ) : null}
      {message.logs.length > 0 ? (
        <details>
          <summary>Reasoning trace ({message.logs.length})</summary>
          <div className="logs">
            {message.logs.map((log, index) => (
              <div key={String(index) + '-' + (log.timestamp || '')} className="log-line">
                <span>{log.timestamp || '--:--:--'}</span>
                <span>{log.message || ''}</span>
              </div>
            ))}
          </div>
        </details>
      ) : null}
    </article>
  );
}

function ThoughtStepCard({ step }: { step: ThoughtStep }): JSX.Element {
  return (
    <article className={'thought-step ' + step.kind}>
      <header>
        <strong>{step.title}</strong>
        <span>{step.time}</span>
      </header>
      {step.content ? <pre>{step.content}</pre> : <p className="muted">No details yet</p>}
    </article>
  );
}

function OutputCard({ output }: { output: ToolOutput }): JSX.Element {
  return (
    <article className="card">
      <h4>{output.type}</h4>
      <p className="muted">{output.time}</p>
      <pre>{output.content}</pre>
    </article>
  );
}

function AgentTeamCodingCard({ view }: { view: AgentTeamCodingView }): JSX.Element {
  return (
    <article className="card">
      <h4>Team Coding</h4>
      <p className="muted">Stage: {view.stage || 'Waiting'}</p>
      {view.groupId ? <p className="muted">Group: {view.groupId}</p> : null}
      {view.repositoryPath ? <p className="muted">Repo: {view.repositoryPath}</p> : null}
      <p className="muted">Tasks: {view.taskCount || view.subTasks.length}</p>
      {view.success !== null ? <p className="muted">Success: {view.success ? 'yes' : 'no'}</p> : null}
      {view.fallbackToSingleAgent ? <p>Fell back to single-agent execution</p> : null}
      {view.integration ? <IntegrationSummary view={view.integration} /> : null}
      {view.subTasks.length > 0 ? (
        <div className="thought-steps">
          {view.subTasks.map((subTask) => (
            <AgentTeamSubTaskCard key={subTask.taskId} subTask={subTask} />
          ))}
        </div>
      ) : (
        <p className="muted">Waiting for structured team coding updates</p>
      )}
    </article>
  );
}

function IntegrationSummary({ view }: { view: NonNullable<AgentTeamCodingView['integration']> }): JSX.Element {
  return (
    <div>
      <p className="muted">
        Integration: {view.integrationBranch || 'pending'} {view.success === null ? '' : `(${view.success ? 'ok' : 'failed'})`}
      </p>
      {view.verification ? <p>{view.verification}</p> : null}
      {view.mergedBranches.length > 0 ? <p>Merged: {view.mergedBranches.join(', ')}</p> : null}
      {view.conflictFiles.length > 0 ? <p>Conflicts: {view.conflictFiles.join(', ')}</p> : null}
      {view.errorMessage ? <p>{view.errorMessage}</p> : null}
    </div>
  );
}

function AgentTeamSubTaskCard({ subTask }: { subTask: AgentTeamCodingSubTaskView }): JSX.Element {
  return (
    <article className="thought-step status">
      <header>
        <strong>{subTask.title || subTask.taskId}</strong>
        <span>{subTask.status || 'PENDING'}</span>
      </header>
      {subTask.goal ? <p>{subTask.goal}</p> : null}
      {subTask.summary ? <pre>{subTask.summary}</pre> : null}
      {subTask.branchName ? <p>Branch: {subTask.branchName}</p> : null}
      {subTask.commitSha ? <p>Commit: {subTask.commitSha}</p> : null}
      {subTask.worktreePath ? <p>Worktree: {subTask.worktreePath}</p> : null}
      {subTask.changedFiles.length > 0 ? <p>Files: {subTask.changedFiles.join(', ')}</p> : null}
      {subTask.ownedPaths.length > 0 ? <p>Owned Paths: {subTask.ownedPaths.join(', ')}</p> : null}
      {subTask.verificationCommands.length > 0 ? <p>Verify: {subTask.verificationCommands.join(' | ')}</p> : null}
      {subTask.testPassed !== null ? <p>Tests: {subTask.testPassed ? 'passed' : 'failed'}</p> : null}
      {subTask.testSummary ? <p>{subTask.testSummary}</p> : null}
      {subTask.conflictRisk ? <p>Conflict Risk: {subTask.conflictRisk}</p> : null}
      {subTask.errorMessage ? <p>{subTask.errorMessage}</p> : null}
    </article>
  );
}

function proxyUrl(url: string, useProxy: boolean): string {
  if (useProxy === false || url.length === 0) {
    return url;
  }
  const encoded = btoa(unescape(encodeURIComponent(url))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return '/api/proxy/web?url=' + encoded;
}

function normalizeUrl(url: string): string {
  const value = url.trim();
  if (value.length === 0) {
    return '';
  }
  if (value.startsWith('http://') || value.startsWith('https://')) {
    return value;
  }
  return 'https://' + value;
}

function buildSnapshotDocument(snapshot: string, currentUrl: string): string {
  const trimmed = snapshot.trim();
  if (/<html[\s>]/i.test(trimmed) || /<!doctype html/i.test(trimmed)) {
    return injectSnapshotBase(trimmed, currentUrl);
  }
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <base href="${escapeHtml(currentUrl)}">
  <style>
    body { margin: 0; padding: 18px; font: 14px/1.6 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #111827; background: #fff; }
    pre { white-space: pre-wrap; word-break: break-word; margin: 0; }
  </style>
</head>
<body><pre>${escapeHtml(snapshot)}</pre></body>
</html>`;
}

function injectSnapshotBase(documentHtml: string, currentUrl: string): string {
  if (currentUrl.length === 0 || /<base[\s>]/i.test(documentHtml)) {
    return documentHtml;
  }
  const baseTag = `<base href="${escapeHtml(currentUrl)}">`;
  const headMatch = /<head[^>]*>/i.exec(documentHtml);
  if (headMatch?.index !== undefined) {
    const insertAt = headMatch.index + headMatch[0].length;
    return documentHtml.slice(0, insertAt) + baseTag + documentHtml.slice(insertAt);
  }
  const htmlMatch = /<html[^>]*>/i.exec(documentHtml);
  if (htmlMatch?.index !== undefined) {
    const insertAt = htmlMatch.index + htmlMatch[0].length;
    return documentHtml.slice(0, insertAt) + `<head>${baseTag}</head>` + documentHtml.slice(insertAt);
  }
  return `<!doctype html><html><head>${baseTag}</head><body>${documentHtml}</body></html>`;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function nowTime(): string {
  return new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}

function isLatestAssistantMessage(messages: ChatMessage[], messageId: string): boolean {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    if (messages[i].role === 'assistant') {
      return messages[i].id === messageId;
    }
  }
  return false;
}

function statusLabel(status: BrowserStatus, previewMode: string): string {
  const labels: Record<BrowserStatus, string> = {
    idle: 'Idle',
    searching: 'Searching',
    'opening-search-page': 'Opening Search Page',
    'results-ready': 'Results Ready',
    'opening-target-page': 'Opening Target Page',
    'proxy-rendered': 'Proxy Rendered',
    'proxy-blocked': 'Proxy Blocked',
    'auto-switch-vnc': 'Auto Switch VNC',
    loading: 'Loading',
    proxying: 'Proxying',
    rendered: 'Rendered',
    blocked: 'Blocked',
    'fallback-to-vnc': 'Fallback to VNC',
    'open-external': 'Open External'
  };
  return `${labels[status]} · ${previewMode}`;
}

function browserStatusTone(status: BrowserStatus): 'idle' | 'connecting' | 'connected' | 'error' {
  if (status === 'idle') {
    return 'idle';
  }
  if (
    status === 'blocked' ||
    status === 'proxy-blocked' ||
    status === 'fallback-to-vnc' ||
    status === 'open-external'
  ) {
    return 'error';
  }
  if (
    status === 'searching' ||
    status === 'loading' ||
    status === 'proxying' ||
    status === 'opening-search-page' ||
    status === 'opening-target-page' ||
    status === 'auto-switch-vnc'
  ) {
    return 'connecting';
  }
  return 'connected';
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
