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

package net.micode.notes.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;

/**
 * 2x规格 笔记桌面小部件提供者
 * 继承自基类 NoteWidgetProvider，专门实现 2x 尺寸小部件的配置
 */
public class NoteWidgetProvider_2x extends NoteWidgetProvider {

    /**
     * 小部件更新回调
     * 系统要求更新小部件时调用，直接调用父类实现统一更新逻辑
     * @param context 上下文
     * @param appWidgetManager 小部件管理器
     * @param appWidgetIds 需要更新的小部件ID数组
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 获取2x小部件对应的布局文件ID
     * 重写父类抽象方法，指定使用 2x 规格的布局
     * @return 布局ID：widget_2x
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_2x;
    }

    /**
     * 根据背景ID获取2x小部件对应的背景资源
     * 重写父类方法，提供2x尺寸专用的背景资源
     * @param bgId 背景编号
     * @return 2x小部件背景资源ID
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId);
    }

    /**
     * 获取当前小部件类型
     * 重写父类方法，标记为 2x 规格小部件
     * @return 小部件类型常量：TYPE_WIDGET_2X
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_2X;
    }
}