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

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 资源解析工具类
 * 统一管理便签应用的背景资源、文字大小、Widget背景等样式资源
 * 提供静态方法供外部获取对应ID的样式/图片资源
 */
public class ResourceParser {

    // 背景颜色常量定义
    public static final int YELLOW           = 0; // 黄色
    public static final int BLUE             = 1; // 蓝色
    public static final int WHITE            = 2; // 白色
    public static final int GREEN            = 3; // 绿色
    public static final int RED              = 4; // 红色

    // 默认背景颜色：黄色
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // 文字大小常量定义
    public static final int TEXT_SMALL       = 0; // 小号字体
    public static final int TEXT_MEDIUM      = 1; // 中号字体
    public static final int TEXT_LARGE       = 2; // 大号字体
    public static final int TEXT_SUPER       = 3; // 超大号字体

    // 默认字体大小：中号
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    /**
     * 编辑页面背景资源内部类
     * 管理便签编辑页的背景、标题栏背景图片
     */
    public static class NoteBgResources {
        // 便签编辑页面背景图片资源数组（对应5种颜色）
        private final static int [] BG_EDIT_RESOURCES = new int [] {
                R.drawable.edit_yellow,
                R.drawable.edit_blue,
                R.drawable.edit_white,
                R.drawable.edit_green,
                R.drawable.edit_red
        };

        // 便签编辑页面标题栏背景图片资源数组
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
                R.drawable.edit_title_yellow,
                R.drawable.edit_title_blue,
                R.drawable.edit_title_white,
                R.drawable.edit_title_green,
                R.drawable.edit_title_red
        };

        /**
         * 根据颜色ID获取编辑页背景图片
         * @param id 颜色ID（0-4）
         * @return 对应背景图片资源ID
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 根据颜色ID获取编辑页标题栏背景图片
         * @param id 颜色ID（0-4）
         * @return 对应标题背景图片资源ID
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 获取默认背景颜色ID
     * 若开启随机背景，则随机返回一种颜色；否则返回默认黄色
     * @param context 上下文
     * @return 背景颜色ID
     */
    public static int getDefaultBgId(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            return BG_DEFAULT_COLOR;
        }
    }

    /**
     * 列表项背景资源内部类
     * 管理便签列表中不同位置（顶部、中间、底部、单独项）的背景图片
     */
    public static class NoteItemBgResources {
        // 列表第一项背景图片
        private final static int [] BG_FIRST_RESOURCES = new int [] {
                R.drawable.list_yellow_up,
                R.drawable.list_blue_up,
                R.drawable.list_white_up,
                R.drawable.list_green_up,
                R.drawable.list_red_up
        };

        // 列表中间项背景图片
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
                R.drawable.list_yellow_middle,
                R.drawable.list_blue_middle,
                R.drawable.list_white_middle,
                R.drawable.list_green_middle,
                R.drawable.list_red_middle
        };

        // 列表最后一项背景图片
        private final static int [] BG_LAST_RESOURCES = new int [] {
                R.drawable.list_yellow_down,
                R.drawable.list_blue_down,
                R.drawable.list_white_down,
                R.drawable.list_green_down,
                R.drawable.list_red_down,
        };

        // 列表只有一项时的背景图片
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
                R.drawable.list_yellow_single,
                R.drawable.list_blue_single,
                R.drawable.list_white_single,
                R.drawable.list_green_single,
                R.drawable.list_red_single
        };

        /**
         * 获取列表第一项背景
         */
        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        /**
         * 获取列表最后一项背景
         */
        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        /**
         * 获取单一项便签背景
         */
        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        /**
         * 获取列表中间项背景
         */
        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        /**
         * 获取文件夹项的固定背景
         */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    /**
     * 桌面小部件（Widget）背景资源内部类
     * 管理2x、4x两种尺寸的Widget背景
     */
    public static class WidgetBgResources {
        // 2x大小小部件背景资源
        private final static int [] BG_2X_RESOURCES = new int [] {
                R.drawable.widget_2x_yellow,
                R.drawable.widget_2x_blue,
                R.drawable.widget_2x_white,
                R.drawable.widget_2x_green,
                R.drawable.widget_2x_red,
        };

        /**
         * 获取2x小部件背景
         * @param id 颜色ID
         * @return 背景资源ID
         */
        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        // 4x大小小部件背景资源
        private final static int [] BG_4X_RESOURCES = new int [] {
                R.drawable.widget_4x_yellow,
                R.drawable.widget_4x_blue,
                R.drawable.widget_4x_white,
                R.drawable.widget_4x_green,
                R.drawable.widget_4x_red
        };

        /**
         * 获取4x小部件背景
         * @param id 颜色ID
         * @return 背景资源ID
         */
        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    /**
     * 文字样式资源内部类
     * 管理四种字号对应的样式资源，并做越界保护
     */
    public static class TextAppearanceResources {
        // 文字样式数组（小、中、大、超大）
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
                R.style.TextAppearanceNormal,
                R.style.TextAppearanceMedium,
                R.style.TextAppearanceLarge,
                R.style.TextAppearanceSuper
        };

        /**
         * 根据字号ID获取对应文字样式
         * 加入越界保护，防止配置错误导致崩溃
         * @param id 字号ID
         * @return 文字样式资源ID
         */
        public static int getTexAppearanceResource(int id) {
            /**
             * HACKME: 修复共享偏好中存储资源ID的bug
             * 若ID超出数组范围，返回默认字体大小
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 获取文字样式总数量（4种）
         * @return 样式数量
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}