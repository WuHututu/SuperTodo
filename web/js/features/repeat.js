'use strict';
function syncSubtasksDone(item) {
  if(Array.isArray(item.subtasks)) item.subtasks.forEach(task => { if(task && typeof task==='object') task.done = !!item.done; });
}

// 下一次重复事项的“露面”时间：下一次截止日（next.due）的本地 00:00。
// 每日重复就是“过 00:00 才出现”；没有可用截止日时退回完成日期的次日 00:00。
// 先建后藏：条目先躺在 repeatQueue 里，到点才放进 items，不需要后台常驻或自启动。
function repeatVisibleFrom(dueStr, now = new Date()) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dueStr || '');
  if (m) return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 0, 0, 0, 0).getTime();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1).getTime();
}

// 每个已完成事项最多派生一次下一次；生成后先进 repeatQueue，不改 items。
function createRepeatItem(store, item, now = new Date()) {
  if(!store || !Array.isArray(store.items)) return;
  const days = Number(item.repeatDays);
  if(!item.done || item.repeatNextId || !Number.isInteger(days) || days < 1 || days > 36500) return;
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  let date = /^\d{4}-\d{2}-\d{2}$/.test(item.due || '') ? Date.parse(item.due + 'T00:00:00Z') : today;
  if(!Number.isFinite(date)) date = today;
  const step = days * 86400000;
  date += Math.max(1, Math.floor((today - date) / step) + 1) * step;
  const next = JSON.parse(JSON.stringify(item));
  next.id = Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
  next.due = new Date(date).toISOString().slice(0, 10);
  next.done = false;
  next.doneScenes = [];
  next.doneTypes = [];
  if(Array.isArray(next.subtasks)) next.subtasks.forEach(task => { if(task && typeof task==='object') task.done = false; });
  next.created = now.getTime();
  delete next.repeatNextId;
  next.visibleFrom = repeatVisibleFrom(next.due, now);
  if(!Array.isArray(store.repeatQueue)) store.repeatQueue = [];
  store.repeatQueue.push(next);
  item.repeatNextId = next.id;
  return next;
}

// 取消完成：只撤回还藏在 repeatQueue 里、尚未露面的那一次；已经出现在列表里的不动。
function dropPendingRepeat(store, item) {
  if(!store || !item || !item.repeatNextId) return false;
  if(!Array.isArray(store.repeatQueue)) return false;
  const idx = store.repeatQueue.findIndex(x => x && x.id === item.repeatNextId);
  if(idx < 0) return false;
  store.repeatQueue.splice(idx, 1);
  delete item.repeatNextId;
  return true;
}

// 到点释放：把 visibleFrom 已过的条目从 repeatQueue 移入 items。
function releaseRepeatQueue(store, now = Date.now()) {
  if(!store || !Array.isArray(store.repeatQueue) || !store.repeatQueue.length) return [];
  const ready = [];
  const rest = [];
  store.repeatQueue.forEach(entry => {
    if(entry && Number(entry.visibleFrom || 0) <= now) ready.push(entry);
    else rest.push(entry);
  });
  if(!ready.length) return [];
  store.repeatQueue = rest;
  if(!Array.isArray(store.items)) store.items = [];
  ready.forEach(entry => { delete entry.visibleFrom; store.items.push(entry); });
  return ready;
}
