/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Google Task 同步管理核心类
 * 负责笔记应用与 Google Task 之间的数据双向同步、冲突处理、增删改查
 * 采用单例模式，全局唯一实例
 */
public class GTaskManager {
    // 日志TAG
    private static final String TAG = GTaskManager.class.getSimpleName();

    // 同步状态常量定义：同步成功
    public static final int STATE_SUCCESS = 0;
    // 网络错误
    public static final int STATE_NETWORK_ERROR = 1;
    // 内部操作错误
    public static final int STATE_INTERNAL_ERROR = 2;
    // 同步正在进行中
    public static final int STATE_SYNC_IN_PROGRESS = 3;
    // 同步已被取消
    public static final int STATE_SYNC_CANCELLED = 4;

    // 单例实例
    private static GTaskManager mInstance = null;

    // 上下文：Activity，用于获取授权Token
    private Activity mActivity;
    // 上下文：Context
    private Context mContext;
    // 内容解析器，操作本地数据库
    private ContentResolver mContentResolver;
    // 是否正在同步
    private boolean mSyncing;
    // 是否取消同步
    private boolean mCancelled;

    // 远程Google任务列表集合：key=任务列表gid，value=任务列表对象
    private HashMap<String, TaskList> mGTaskListHashMap;
    // 所有远程节点（任务/任务列表）集合：key=gid，value=节点对象
    private HashMap<String, Node> mGTaskHashMap;
    // 元数据集合：key=关联gid，value=元数据对象
    private HashMap<String, MetaData> mMetaHashMap;
    // 元数据专用任务列表（存储同步元信息）
    private TaskList mMetaList;
    // 本地已删除笔记ID集合，用于批量删除
    private HashSet<Long> mLocalDeleteIdMap;
    // Google任务ID -> 本地笔记ID映射
    private HashMap<String, Long> mGidToNid;
    // 本地笔记ID -> Google任务ID映射
    private HashMap<Long, String> mNidToGid;

    /**
     * 私有构造方法（单例模式）
     * 初始化所有同步相关集合与状态标志
     */
    private GTaskManager() {
        mSyncing = false;
        mCancelled = false;
        mGTaskListHashMap = new HashMap<String, TaskList>();
        mGTaskHashMap = new HashMap<String, Node>();
        mMetaHashMap = new HashMap<String, MetaData>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<Long>();
        mGidToNid = new HashMap<String, Long>();
        mNidToGid = new HashMap<Long, String>();
    }

    /**
     * 获取单例实例（线程安全）
     * @return GTaskManager唯一实例
     */
    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    /**
     * 设置Activity上下文，用于Google账号授权
     * @param activity 当前Activity
     */
    public synchronized void setActivityContext(Activity activity) {
        // used for getting authtoken
        mActivity = activity;
    }

    /**
     * 执行同步主方法
     * 完成登录、拉取远程数据、本地与远程双向同步
     * @param context 上下文
     * @param asyncTask 异步任务，用于更新同步进度
     * @return 同步状态码
     */
    public int sync(Context context, GTaskASyncTask asyncTask) {
        // 如果正在同步，直接返回进行中状态
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress");
            return STATE_SYNC_IN_PROGRESS;
        }
        // 初始化上下文与解析器
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        // 设置同步状态
        mSyncing = true;
        mCancelled = false;
        // 清空所有缓存数据
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();

        try {
            // 获取Google任务客户端实例并重置更新队列
            GTaskClient client = GTaskClient.getInstance();
            client.resetUpdateArray();

            // login google task
            // 未取消则执行Google账号登录
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw new NetworkFailureException("login google task failed");
                }
            }

            // get the task list from google
            // 更新进度：初始化任务列表
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            // 初始化远程Google任务列表
            initGTaskList();

            // do content sync work
            // 更新进度：同步数据
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            // 执行核心数据同步
            syncContent();
        } catch (NetworkFailureException e) {
            // 网络异常处理
            Log.e(TAG, e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {
            // 操作失败异常处理
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            // 其他未知异常处理
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {
            // 无论成功失败，清空缓存并重置同步状态
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;
        }

        // 根据取消标志返回对应状态
        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }

    /**
     * 初始化Google任务列表
     * 从远程拉取任务列表、元数据列表、所有任务数据
     * @throws NetworkFailureException 网络异常
     */
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled)
            return;
        GTaskClient client = GTaskClient.getInstance();
        try {
            // 获取远程所有任务列表
            JSONArray jsTaskLists = client.getTaskLists();

            // init meta list first
            // 优先初始化元数据列表（存储同步附加信息）
            mMetaList = null;
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 查找MIUI定义的元数据专用文件夹
                if (name
                        .equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);

                    // load meta data
                    // 加载元数据列表下的所有元数据
                    JSONArray jsMetas = client.getTaskList(gid);
                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(object);
                        // 只保存有效元数据
                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                            }
                        }
                    }
                }
            }

            // create meta list if not existed
            // 如果元数据列表不存在，则创建
            if (mMetaList == null) {
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
            }

            // init task list
            // 初始化所有MIUI同步任务列表（排除元数据列表）
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 匹配MIUI前缀的文件夹
                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                        && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META)) {
                    TaskList tasklist = new TaskList();
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    // load tasks
                    // 加载当前任务列表下的所有任务
                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                        Task task = new Task();
                        task.setContentByRemoteJSON(object);
                        if (task.isWorthSaving()) {
                            // 关联元数据
                            task.setMetaInfo(mMetaHashMap.get(gid));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(gid, task);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            // JSON解析异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }

    /**
     * 执行内容同步核心逻辑
     * 处理本地删除、文件夹同步、笔记同步、远程新增数据
     * @throws NetworkFailureException 网络异常
     */
    private void syncContent() throws NetworkFailureException {
        int syncType;
        Cursor c = null;
        String gid;
        Node node;

        mLocalDeleteIdMap.clear();

        if (mCancelled) {
            return;
        }

        // for local deleted note
        // 处理本地已移至回收站的笔记：同步删除远程数据
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        // 执行远程删除操作
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);
                    }
                    // 记录本地待删除ID
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
            } else {
                Log.w(TAG, "failed to query trash folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // sync folder first
        // 优先同步文件夹（目录结构）
        syncFolder();

        // for note existing in database
        // 同步数据库中存在的正常笔记
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        // 远程存在该笔记：更新映射关系
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        // 获取同步类型（更新/冲突/无操作）
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 本地新增：无GID，需要上传到远程
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // 远程已删除：需要删除本地
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    // 执行同步操作
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing note in database");
            }

        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // go through remaining items
        // 处理远程剩余数据：本地不存在，需要新增到本地
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }

        // mCancelled can be set by another thread, so we neet to check one by
        // one
        // clear local delete table
        // 批量删除本地已标记的笔记数据
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw new ActionFailureException("failed to batch-delete local deleted notes");
            }
        }

        // refresh local sync id
        // 提交远程更新，并刷新本地同步ID/时间戳
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
            refreshLocalSyncId();
        }

    }

    /**
     * 同步文件夹（目录）
     * 包括根目录、通话记录目录、用户自定义文件夹
     * @throws NetworkFailureException 网络异常
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;
        String gid;
        Node node;
        int syncType;

        if (mCancelled) {
            return;
        }

        // for root folder
        // 同步根文件夹
        try {
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);
                if (node != null) {
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    // for system folder, only update remote name if necessary
                    // 系统文件夹：仅在名称不匹配时更新远程
                    if (!node.getName().equals(
                            GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT))
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                } else {
                    // 远程不存在，创建远程文件夹
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            } else {
                Log.w(TAG, "failed to query root folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // for call-note folder
        // 同步通话记录文件夹
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                            String.valueOf(Notes.ID_CALL_RECORD_FOLDER)
                    }, null);
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                        // for system folder, only update remote name if
                        // necessary
                        if (!node.getName().equals(
                                GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                        + GTaskStringUtils.FOLDER_CALL_NOTE))
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    } else {
                        doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                    }
                }
            } else {
                Log.w(TAG, "failed to query call note folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // for local existing folders
        // 同步本地存在的用户自定义文件夹
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // local add
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // remote delete
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // for remote add folders
        // 处理远程新增文件夹：同步到本地
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            gid = entry.getKey();
            node = entry.getValue();
            if (mGTaskHashMap.containsKey(gid)) {
                mGTaskHashMap.remove(gid);
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
            }
        }

        // 提交远程更新
        if (!mCancelled)
            GTaskClient.getInstance().commitUpdate();
    }

    /**
     * 执行具体的同步操作
     * 根据同步类型分发：增/删/改/冲突处理
     * @param syncType 同步操作类型
     * @param node 远程节点对象
     * @param c 本地数据游标
     * @throws NetworkFailureException 网络异常
     */
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        MetaData meta;
        switch (syncType) {
            // 远程数据新增到本地
            case Node.SYNC_ACTION_ADD_LOCAL:
                addLocalNode(node);
                break;
            // 本地数据新增到远程
            case Node.SYNC_ACTION_ADD_REMOTE:
                addRemoteNode(node, c);
                break;
            // 远程已删除，删除本地
            case Node.SYNC_ACTION_DEL_LOCAL:
                meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                break;
            // 本地已删除，删除远程
            case Node.SYNC_ACTION_DEL_REMOTE:
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                GTaskClient.getInstance().deleteNode(node);
                break;
            // 用远程数据更新本地
            case Node.SYNC_ACTION_UPDATE_LOCAL:
                updateLocalNode(node, c);
                break;
            // 用本地数据更新远程
            case Node.SYNC_ACTION_UPDATE_REMOTE:
                updateRemoteNode(node, c);
                break;
            // 同步冲突：默认以本地为准覆盖远程
            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                // merging both modifications maybe a good idea
                // right now just use local update simply
                updateRemoteNode(node, c);
                break;
            // 无同步操作
            case Node.SYNC_ACTION_NONE:
                break;
            // 未知同步类型，抛出异常
            case Node.SYNC_ACTION_ERROR:
            default:
                throw new ActionFailureException("unkown sync action type");
        }
    }

    /**
     * 将远程节点数据添加到本地数据库
     * @param node 远程节点（任务/文件夹）
     * @throws NetworkFailureException 网络异常
     */
    private void addLocalNode(Node node) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        // 处理文件夹类型节点
        if (node instanceof TaskList) {
            if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                // 根文件夹
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                // 通话记录文件夹
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                // 普通文件夹
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
            }
        } else {
            // 处理笔记/任务类型节点
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();
            try {
                // 检查笔记ID是否已存在，避免冲突
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            // the id is not available, have to create a new one
                            note.remove(NoteColumns.ID);
                        }
                    }
                }

                // 检查笔记内容数据ID是否冲突
                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                // the data id is not available, have to create
                                // a new one
                                data.remove(DataColumns.ID);
                            }
                        }
                    }

                }
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                e.printStackTrace();
            }
            sqlNote.setContent(js);

            // 设置父文件夹ID
            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                Log.e(TAG, "cannot find task's parent id locally");
                throw new ActionFailureException("cannot add local node");
            }
            sqlNote.setParentId(parentId.longValue());
        }

        // create the local node
        // 保存到本地数据库
        sqlNote.setGtaskId(node.getGid());
        sqlNote.commit(false);

        // update gid-nid mapping
        // 更新ID映射关系
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());

        // update meta
        // 更新远程元数据
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 用远程数据更新本地笔记
     * @param node 远程节点
     * @param c 本地数据游标
     * @throws NetworkFailureException 网络异常
     */
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        // update the note locally
        // 根据游标创建本地笔记对象
        sqlNote = new SqlNote(mContext, c);
        sqlNote.setContent(node.getLocalJSONFromContent());

        // 设置父文件夹ID
        Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
                : new Long(Notes.ID_ROOT_FOLDER);
        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally");
            throw new ActionFailureException("cannot update local node");
        }
        sqlNote.setParentId(parentId.longValue());
        sqlNote.commit(true);

        // update meta info
        // 更新元数据
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 将本地笔记添加/同步到远程Google Task
     * @param node 远程节点
     * @param c 本地数据游标
     * @throws NetworkFailureException 网络异常
     */
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        Node n;

        // update remotely
        // 处理笔记类型：创建远程Task
        if (sqlNote.isNoteType()) {
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());

            // 获取父文件夹GID
            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot add remote task");
            }
            mGTaskListHashMap.get(parentGid).addChildTask(task);

            // 创建远程任务
            GTaskClient.getInstance().createTask(task);
            n = (Node) task;

            // add meta
            // 更新元数据
            updateRemoteMeta(task.getGid(), sqlNote);
        } else {
            // 处理文件夹类型：创建远程TaskList
            TaskList tasklist = null;

            // we need to skip folder if it has already existed
            // 生成远程文件夹名称
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER)
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER)
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            else
                folderName += sqlNote.getSnippet();

            // 检查远程是否已存在同名文件夹
            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();

                if (list.getName().equals(folderName)) {
                    tasklist = list;
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);
                    }
                    break;
                }
            }

            // 不存在则创建新文件夹
            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }
            n = (Node) tasklist;
        }

        // update local note
        // 将远程GID更新到本地数据库
        sqlNote.setGtaskId(n.getGid());
        sqlNote.commit(false);
        sqlNote.resetLocalModified();
        sqlNote.commit(true);

        // gid-id mapping
        // 更新ID映射
        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
    }

    /**
     * 用本地数据更新远程Google Task
     * @param node 远程节点
     * @param c 本地数据游标
     * @throws NetworkFailureException 网络异常
     */
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);

        // update remotely
        // 用本地数据覆盖远程节点
        node.setContentByLocalJSON(sqlNote.getContent());
        GTaskClient.getInstance().addUpdateNode(node);

        // update meta
        // 更新元数据
        updateRemoteMeta(node.getGid(), sqlNote);

        // move task if necessary
        // 如果父文件夹变化，移动远程任务
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent();

            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot update remote task");
            }
            TaskList curParentList = mGTaskListHashMap.get(curParentGid);

            // 父文件夹不同则执行移动操作
            if (preParentList != curParentList) {
                preParentList.removeChildTask(task);
                curParentList.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }

        // clear local modified flag
        // 重置本地修改标志
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
    }

    /**
     * 更新远程元数据（存储笔记附加信息）
     * @param gid 任务GID
     * @param sqlNote 本地笔记对象
     * @throws NetworkFailureException 网络异常
     */
    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        if (sqlNote != null && sqlNote.isNoteType()) {
            MetaData metaData = mMetaHashMap.get(gid);
            if (metaData != null) {
                // 元数据已存在：更新
                metaData.setMeta(gid, sqlNote.getContent());
                GTaskClient.getInstance().addUpdateNode(metaData);
            } else {
                // 元数据不存在：创建
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }

    /**
     * 刷新本地同步ID/时间戳
     * 同步完成后更新本地最后同步时间
     * @throws NetworkFailureException 网络异常
     */
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // get the latest gtask list
        // 重新拉取最新远程数据
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();

        Cursor c = null;
        try {
            // 查询所有非系统、非回收站笔记
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        // 更新最后修改时间
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                    } else {
                        Log.e(TAG, "something is missed");
                        throw new ActionFailureException(
                                "some local items don't have gid after sync");
                    }
                }
            } else {
                Log.w(TAG, "failed to query local note to refresh sync id");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    }

    /**
     * 获取当前同步的Google账号
     * @return 账号名称
     */
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }

    /**
     * 取消同步操作
     * 设置取消标志，同步方法会检测并中断流程
     */
    public void cancelSync() {
        mCancelled = true;
    }
}