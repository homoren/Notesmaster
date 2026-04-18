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

package net.micode.notes.tool;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * 数据工具类
 * 提供便签应用核心的数据操作方法：批量删除、移动、查询、文件夹管理、通话记录关联等
 * 所有方法均为静态方法，直接通过ContentResolver操作ContentProvider
 */
public class DataUtils {
    // 日志TAG
    public static final String TAG = "DataUtils";

    /**
     * 批量删除便签/文件夹
     * @param resolver 内容解析器，用于操作数据库
     * @param ids 待删除的条目ID集合
     * @return 删除成功返回true，失败返回false
     */
    public static boolean batchDeleteNotes(ContentResolver resolver, HashSet<Long> ids) {
        // 集合为空，直接返回成功
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }
        // 集合无数据，直接返回成功
        if (ids.size() == 0) {
            Log.d(TAG, "no id is in the hashset");
            return true;
        }

        // 批量操作集合，用于执行多条数据库删除指令
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            // 禁止删除系统根文件夹
            if(id == Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't delete system folder root");
                continue;
            }
            // 构建删除操作，根据ID删除对应便签
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            operationList.add(builder.build());
        }
        try {
            // 执行批量操作
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            // 操作结果为空则删除失败
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            // 跨进程异常
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            // 批量操作执行异常
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 将单个便签从原文件夹移动到目标文件夹
     * @param resolver 内容解析器
     * @param id 便签ID
     * @param srcFolderId 原文件夹ID
     * @param desFolderId 目标文件夹ID
     */
    public static void moveNoteToFoler(ContentResolver resolver, long id, long srcFolderId, long desFolderId) {
        ContentValues values = new ContentValues();
        // 更新父文件夹ID为目标文件夹
        values.put(NoteColumns.PARENT_ID, desFolderId);
        // 记录原始父文件夹ID
        values.put(NoteColumns.ORIGIN_PARENT_ID, srcFolderId);
        // 标记本地数据已修改
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        // 执行更新操作
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id), values, null, null);
    }

    /**
     * 批量移动便签到指定文件夹
     * @param resolver 内容解析器
     * @param ids 待移动的便签ID集合
     * @param folderId 目标文件夹ID
     * @return 移动成功返回true，失败返回false
     */
    public static boolean batchMoveToFolder(ContentResolver resolver, HashSet<Long> ids,
                                            long folderId) {
        // ID集合为空，直接返回成功
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }

        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            // 构建更新操作
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            // 设置目标父文件夹
            builder.withValue(NoteColumns.PARENT_ID, folderId);
            // 标记本地修改
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);
            operationList.add(builder.build());
        }

        try {
            // 执行批量更新
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 获取用户自定义文件夹数量（排除系统文件夹和回收站）
     * @param resolver 内容解析器
     * @return 文件夹数量
     */
    public static int getUserFolderCount(ContentResolver resolver) {
        // 查询条件：类型为文件夹，且父ID不是回收站
        Cursor cursor =resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { "COUNT(*)" },
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)},
                null);

        int count = 0;
        if(cursor != null) {
            if(cursor.moveToFirst()) {
                try {
                    // 获取统计数量
                    count = cursor.getInt(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "get folder count failed:" + e.toString());
                } finally {
                    // 关闭游标防止内存泄漏
                    cursor.close();
                }
            }
        }
        return count;
    }

    /**
     * 判断指定ID的便签是否在数据库中可见（非回收站、类型匹配）
     * @param resolver 内容解析器
     * @param noteId 便签ID
     * @param type 便签类型
     * @return 可见返回true，不可见返回false
     */
    public static boolean visibleInNoteDatabase(ContentResolver resolver, long noteId, int type) {
        // 查询条件：类型匹配，且不在回收站中
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null,
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER,
                new String [] {String.valueOf(type)},
                null);

        boolean exist = false;
        if (cursor != null) {
            // 有数据则表示存在
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 判断指定ID的便签是否存在于数据库中
     * @param resolver 内容解析器
     * @param noteId 便签ID
     * @return 存在返回true，不存在返回false
     */
    public static boolean existInNoteDatabase(ContentResolver resolver, long noteId) {
        // 根据ID直接查询
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 判断指定ID的便签内容数据是否存在
     * @param resolver 内容解析器
     * @param dataId 内容数据ID
     * @return 存在返回true，不存在返回false
     */
    public static boolean existInDataDatabase(ContentResolver resolver, long dataId) {
        // 查询内容数据表
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查指定名称的文件夹是否已存在（非回收站）
     * @param resolver 内容解析器
     * @param name 文件夹名称
     * @return 存在返回true，不存在返回false
     */
    public static boolean checkVisibleFolderName(ContentResolver resolver, String name) {
        // 查询条件：类型为文件夹、不在回收站、名称匹配
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, null,
                NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER +
                        " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
                        " AND " + NoteColumns.SNIPPET + "=?",
                new String[] { name }, null);
        boolean exist = false;
        if(cursor != null) {
            if(cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 获取指定文件夹下绑定的桌面小部件集合
     * @param resolver 内容解析器
     * @param folderId 文件夹ID
     * @return 小部件属性集合
     */
    public static HashSet<AppWidgetAttribute> getFolderNoteWidget(ContentResolver resolver, long folderId) {
        // 查询文件夹下的小部件ID和类型
        Cursor c = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE },
                NoteColumns.PARENT_ID + "=?",
                new String[] { String.valueOf(folderId) },
                null);

        HashSet<AppWidgetAttribute> set = null;
        if (c != null) {
            if (c.moveToFirst()) {
                set = new HashSet<AppWidgetAttribute>();
                do {
                    try {
                        // 封装小部件属性
                        AppWidgetAttribute widget = new AppWidgetAttribute();
                        widget.widgetId = c.getInt(0);
                        widget.widgetType = c.getInt(1);
                        set.add(widget);
                    } catch (IndexOutOfBoundsException e) {
                        Log.e(TAG, e.toString());
                    }
                } while (c.moveToNext());
            }
            c.close();
        }
        return set;
    }

    /**
     * 根据便签ID获取关联的通话电话号码
     * @param resolver 内容解析器
     * @param noteId 便签ID
     * @return 电话号码，无数据返回空字符串
     */
    public static String getCallNumberByNoteId(ContentResolver resolver, long noteId) {
        // 查询条件：便签ID匹配，类型为通话记录
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String [] { CallNote.PHONE_NUMBER },
                CallNote.NOTE_ID + "=? AND " + CallNote.MIME_TYPE + "=?",
                new String [] { String.valueOf(noteId), CallNote.CONTENT_ITEM_TYPE },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                return cursor.getString(0);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Get call number fails " + e.toString());
            } finally {
                cursor.close();
            }
        }
        return "";
    }

    /**
     * 根据电话号码和通话日期查询对应的便签ID
     * @param resolver 内容解析器
     * @param phoneNumber 电话号码
     * @param callDate 通话日期
     * @return 便签ID，无数据返回0
     */
    public static long getNoteIdByPhoneNumberAndCallDate(ContentResolver resolver, String phoneNumber, long callDate) {
        // 查询通话记录对应的便签
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String [] { CallNote.NOTE_ID },
                CallNote.CALL_DATE + "=? AND " + CallNote.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                        + CallNote.PHONE_NUMBER + ",?)",
                new String [] { String.valueOf(callDate), CallNote.CONTENT_ITEM_TYPE, phoneNumber },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    return cursor.getLong(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Get call note id fails " + e.toString());
                }
            }
            cursor.close();
        }
        return 0;
    }

    /**
     * 根据便签ID获取便签摘要内容
     * @param resolver 内容解析器
     * @param noteId 便签ID
     * @return 摘要内容
     * @throws IllegalArgumentException 便签不存在时抛出异常
     */
    public static String getSnippetById(ContentResolver resolver, long noteId) {
        // 查询摘要字段
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String [] { NoteColumns.SNIPPET },
                NoteColumns.ID + "=?",
                new String [] { String.valueOf(noteId)},
                null);

        if (cursor != null) {
            String snippet = "";
            if (cursor.moveToFirst()) {
                snippet = cursor.getString(0);
            }
            cursor.close();
            return snippet;
        }
        // 无数据抛出异常
        throw new IllegalArgumentException("Note is not found with id: " + noteId);
    }

    /**
     * 格式化便签摘要：去除首尾空格，只保留第一行内容
     * @param snippet 原始摘要
     * @return 格式化后的摘要
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet != null) {
            // 去除首尾空格
            snippet = snippet.trim();
            // 找到换行符位置
            int index = snippet.indexOf('\n');
            if (index != -1) {
                // 只截取第一行
                snippet = snippet.substring(0, index);
            }
        }
        return snippet;
    }
}