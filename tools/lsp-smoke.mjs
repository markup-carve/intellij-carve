import { spawn } from 'node:child_process';
import { resolve } from 'node:path';

const server = process.argv[2];
if (!server) {
  console.error('Usage: node tools/lsp-smoke.mjs <server.js>');
  process.exit(64);
}
const child = spawn(process.execPath, [resolve(server), '--stdio'], { stdio: ['pipe', 'pipe', 'pipe'] });
let buffer = Buffer.alloc(0);
let nextId = 1;
const pending = new Map();
let stderr = '';
const timer = setTimeout(() => fail('Timed out after 20 seconds'), 20_000);
child.stderr.on('data', chunk => { stderr += chunk.toString(); });
child.on('error', error => fail(error.message));
child.on('exit', (code, signal) => {
  if (pending.size) fail(`Server exited early (${code ?? signal}): ${stderr}`);
  clearTimeout(timer);
});
function fail(message) {
  clearTimeout(timer);
  child.kill();
  console.error(message);
  process.exit(1);
}
function send(message) {
  const body = Buffer.from(JSON.stringify({ jsonrpc: '2.0', ...message }));
  child.stdin.write(`Content-Length: ${body.length}\r\n\r\n`);
  child.stdin.write(body);
}
function request(method, params) {
  const id = nextId++;
  send({ id, method, params });
  return new Promise((resolveResult, rejectResult) => pending.set(id, { resolveResult, rejectResult }));
}
child.stdout.on('data', chunk => {
  buffer = Buffer.concat([buffer, chunk]);
  while (true) {
    const split = buffer.indexOf('\r\n\r\n');
    if (split < 0) return;
    const match = /^Content-Length:\s*(\d+)$/im.exec(buffer.subarray(0, split).toString());
    if (!match) fail('Missing Content-Length header');
    const end = split + 4 + Number(match[1]);
    if (buffer.length < end) return;
    const message = JSON.parse(buffer.subarray(split + 4, end).toString());
    buffer = buffer.subarray(end);
    if (message.method && message.id !== undefined) {
      const result = message.method === 'workspace/configuration'
        ? Array((message.params?.items ?? []).length).fill(null) : null;
      send({ id: message.id, result });
    } else if (message.id !== undefined && pending.has(message.id)) {
      const handler = pending.get(message.id);
      pending.delete(message.id);
      message.error ? handler.rejectResult(new Error(JSON.stringify(message.error))) : handler.resolveResult(message.result);
    }
  }
});
try {
  await request('initialize', { processId: process.pid, rootUri: null, capabilities: {} });
  send({ method: 'initialized', params: {} });
  const uri = 'file:///smoke/table.crv';
  send({ method: 'textDocument/didOpen', params: { textDocument: {
    uri, languageId: 'carve', version: 1, text: '| a | bb |\n|---|---|\n| ccc | d |\n',
  } } });
  const actions = await request('textDocument/codeAction', {
    textDocument: { uri },
    range: { start: { line: 0, character: 0 }, end: { line: 2, character: 11 } },
    context: { diagnostics: [] },
  });
  const titles = (actions ?? []).map(action => action.title);
  console.log(`Code actions: ${titles.join(', ') || '(none)'}`);
  if (!titles.includes('Align table')) fail('Missing Align table code action');
  await request('shutdown', null);
  send({ method: 'exit' });
  child.stdin.end();
} catch (error) {
  fail(`LSP smoke failed: ${error.message}; ${stderr}`);
}
