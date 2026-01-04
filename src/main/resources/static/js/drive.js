const drive = "local";

/**
 * 打开文件
 * @param docId
 * @param action 打开方式 view or edit
 * @param isInFrame 是否在当前标签页打开
 * @param callback 是否是在当前标签页打开
 */
function openDoc(docId, action, isInFrame, callback) {
    if (!docId) return;
    showToastInParent(`打开失败: 请自行实现打开文件逻辑`);
}

/**
 * 当前页面弹窗下载
 * @param docId
 */
function downloadDoc(docId) {
    if (!docId) return;
    window.open(`/v2/context/file/download?docId=${docId}`);
}

/**
 * 上传文件
 */
function uploadDoc() {
    $('#fileUpload').click();
}

/**
 * 删除文件
 */
function deleteDoc(docId) {
    if (!docId) return;
    const url = `/v2/context/file/delete/${docId}?driveId=${drive}`;
    $.get(url, function (data, status) {
        console.log('delete doc success');
        // remove file, 找到id为file-list的元素中th data-id为docId的元素，删除相应的tr元素
        document.getElementById("file-list").querySelector(`td[data-id="${docId}"]`).parentElement.remove();
        // 给父页面发消息
        showToastInParent(`${data.name}删除成功`);
     }).fail(function (xhr, status, error) {
        handleError('删除', xhr, status, error);
    });
}

/**
 * 上传文件
 * @param file
 */
function uploadFile(file) {
    if (!file) return;
    const url = `/v2/context/file/upload`;
    const formData = new FormData();
    formData.append('file', file, file.name);
    // 获取drive-list中nav-link是active的drive
    formData.append('drive', drive);
    $.ajax({
        url,
        type: 'POST',
        data: formData,
        processData: false,
        contentType: false,
        success: function (data) {
            if (!data) return;
            console.log('data: ', data);
            if (data.id) {
                console.log('upload doc success');
                addDocToList(data);
                eraseFileInput($('#fileUpload'));
                showToastInParent(`${data.name}上传成功`)
            }
        },
        error: function (xhr, status, error) {
            eraseFileInput($('#fileUpload'));
            handleError('文件上传', xhr, status, error);
        }
    });
}

/**
 * 擦除文件选择框中选择的文件
 * @param jqFileInput
 */
function eraseFileInput(jqFileInput) {
    jqFileInput.wrap('<form>').closest('form').get(0).reset();
    jqFileInput.unwrap();
}

/**
 * 为文件添加鼠标点击事件
 * @param doc
 */
function addDocToList(doc) {

    // 编辑按钮
    const editBtnDiv = document.createElement('div');
    editBtnDiv.className = 'op-btn';
    editBtnDiv.addEventListener('click', function () { openDoc(doc.id, 'edit'); });
    const editBtnSpan = document.createElement('span');
    editBtnSpan.setAttribute('data-feather', 'edit');
    editBtnDiv.append(editBtnSpan);
    // 下载按钮
    const downloadBtnDiv = document.createElement('div');
    downloadBtnDiv.className = 'op-btn';
    downloadBtnDiv.addEventListener('click', function () { downloadDoc(doc.id); });
    const downloadBtnSpan = document.createElement('span');
    downloadBtnSpan.setAttribute('data-feather', 'download');
    downloadBtnDiv.append(downloadBtnSpan);
    // 删除按钮
    const deleteBtnDiv = document.createElement('div');
    deleteBtnDiv.className = 'op-btn';
    deleteBtnDiv.addEventListener('click', function () { deleteDoc(doc.id); });
    const deleteBtnSpan = document.createElement('span');
    deleteBtnSpan.setAttribute('data-feather', 'trash-2');
    deleteBtnDiv.append(deleteBtnSpan);
    // 更多按钮
    const opMoreBtnDiv = document.createElement('div');
    opMoreBtnDiv.className = 'op-btn';
    opMoreBtnDiv.addEventListener('click', function () { onFileOpClick(doc.id); });
    const opMoreBtnSpan = document.createElement('span');
    opMoreBtnSpan.setAttribute('data-feather', 'more-horizontal');
    opMoreBtnDiv.append(opMoreBtnSpan);

    // 按钮组
    const opBtnDiv = document.createElement('div');
    opBtnDiv.className = 'multi-btn';
    opBtnDiv.append(editBtnDiv);
    opBtnDiv.append(downloadBtnDiv);
    opBtnDiv.append(deleteBtnDiv);
    opBtnDiv.append(opMoreBtnDiv);

    const opBtnTd = document.createElement('td');
    opBtnTd.setAttribute('data-id', doc.id);
    opBtnTd.append(opBtnDiv);
    const lastTd = document.createElement('td');

    // 新建span元素, 设置data-feather属性为file-text，class为mr-2
    const span = document.createElement('span');
    span.setAttribute('data-feather', 'file-text');
    span.className = 'mr-2';

    // 新建td元素，设置scope属性为row，data-id属性为doc.id，class为file-list-item
    const filenameTh = document.createElement('td');
    filenameTh.setAttribute('scope', 'row');
    filenameTh.setAttribute('data-id', doc.id);
    filenameTh.className = 'file-list-item';
    filenameTh.onclick = onenFileClick; // 添加按键事件
    filenameTh.append(span);
    filenameTh.append(doc.name);

    // 新建tr元素，设置class属性为file-row
    const tr = document.createElement('tr');
    tr.className = 'file-row';
    tr.append(filenameTh);
    tr.append(opBtnTd);
    tr.append(lastTd);

    // 在id为file-list的元素中添加tr元素
    const fileListEle = document.getElementById('file-list');
    fileListEle.insertBefore(tr, fileListEle.firstChild);

    // 重新渲染feather
    feather.replace();
}

/**
 * 每个class为list-group-item的元素的点击事件，点击是新窗口打开
 * @param event
 */
function onenFileClick(event) {
    const docId = event.target.attributes['data-id'].value;
    if (!docId) return;
    openDoc(docId, 'view');
}

/**
 * 打开模态框
 * @param title
 * @param src
 */
function openFileOpModal(title, src) {
    const modalTitle = document.getElementById('fileOpModalLabel');
    const modalIframe = document.getElementById('fileOpIframe');
    modalTitle.innerText = title;
    modalIframe.src = src;
    // bootstarap modal show
    $('#fileOpModal').modal('show').modal('handleUpdate')
}

/**
 * 关闭模态框
 */
function closeFileOpModal() {
    $('#fileOpModal').modal('hide');
}


/**
 * 发布在当前页面打开文件的消息
 */
function openInCurrentTab(docId, action) {
    openDoc(docId, action, true, (url) => {
        window.parent.postMessage({
            type: 'updateFrameSrc',
            msg: {
                docId,
                url
            }
        }, '*');
    })
}

/**
 * 发布消息提示的通知
 */
function showToastInParent(msg) {
    window.parent.postMessage({
        type: 'showToast',
        msg
    }, '*');
}

/**
 * 统一的错误处理函数
 */
function handleError(operation, xhr, status, error) {
    const errorMsg = xhr.responseText || `${status}: ${error}` || '未知错误';
    showToastInParent(`${operation}失败: ${errorMsg}`);
}

/**
 * 维护一个单例的ContextMenu
 */
let menu = null;
function getMenu(actions) {
    if (!menu) {
        menu = new ContextMenu(actions);
    }
    return menu;
}
const menuActions = [
    {
        name: '当前页面编辑',
        id: 'editInCurrentTab',
        onClick: function () {
            const docId = this.docId;
            if (!docId) return;
            openInCurrentTab(docId, 'edit');
        }
    },
    {
        name: '当前页面预览',
        id: 'viewInCurrentTab',
        onClick: function () {
            const docId = this.docId;
            if (!docId) return;
            openInCurrentTab(docId, 'view');
        }
    },
    {
        name: '自定义文档元数据',
        id: 'meta',
        onClick: function () {
            const docId = this.docId;
            if (!docId) return;
            openFileOpModal('修改元数据', `/home/meta/${docId}`)
        }
    }
];

/**
 * 更多菜单
 */
function onFileOpClick(docId) {
    if (!docId) return;

    const menu = getMenu(menuActions);
    menu.docId = docId;
    // 菜单显示在当前元素的右边,Y轴位置与鼠标位置一致,需要增加滚动条的高度
    menu.menu.style.left = event.clientX + 'px';
    menu.menu.style.top = event.clientY + document.documentElement.scrollTop + 'px';
    // 显示菜单
    menu.show();
    // 鼠标离开菜单时隐藏
    menu.menu.addEventListener('mouseleave', function () {
        menu.hide();
    });
    // jQuery找到tr data-id为docId的元素
    const fileRow = $(`tr[data-id="${docId}"]`)[0];
    //鼠标在菜单上时激活css hover效果
    menu.menu.addEventListener('mouseenter', function () {
        // force fileRow state :hover
        if (fileRow.classList) {
            fileRow.classList.add('file-row-hover');
        }
        fileRow.classList.add('file-row-hover');
        // 其他tr元素取消css hover效果
        const fileRows = document.getElementsByClassName('file-row');
        for (let i = 0; i < fileRows.length; i++) {
            if (fileRows[i] !== fileRow) {
                fileRows[i].classList.remove('file-row-hover');
            }
        }
    });
    // 鼠标离开菜单时取消css hover效果
    menu.menu.addEventListener('mouseleave', function () {
        fileRow.classList.remove('file-row-hover');
    });
}

/**
 * 定义ContextMenu,菜单始终显示在鼠标右键的位置
 */
class ContextMenu {
    constructor(actions) {
        this.menu = document.createElement('div');
        this.menu.className = 'context-menu';
        this.menu.style.left = event.clientX + 'px';
        this.menu.style.top = event.clientY + 'px';
        this.menu.style.display = 'absolute';

        this.menuItems = [];
        for (let i = 0; i < actions.length; i++) {
            const menuItem = document.createElement('div');
            menuItem.className = 'context-menu-item';
            menuItem.innerText = actions[i].name;
            menuItem.addEventListener('click', actions[i].onClick.bind(this));
            this.menuItems.push(menuItem);
            this.menu.appendChild(menuItem);
            // 鼠标移入时高亮
            menuItem.addEventListener('mouseenter', function () {
                menuItem.style.backgroundColor = '#ccc';
            });
            // 鼠标移出时取消高亮
            menuItem.addEventListener('mouseleave', function () {
                menuItem.style.backgroundColor = '#fff';
            });
            menuItem.addEventListener('click', function () {
                if (this.menu) this.menu.hide();
            });
        }
        document.body.appendChild(this.menu);
    }

    show() { this.menu.style.display = 'block'; }

    hide() { this.menu.style.display = 'none'; }
}

/**
 * 点击文件名th时，打开一个搜索框，输入内容后，搜索文件名包含该内容的文件
 */
function searchFile() {
    const searchText = document.getElementById('search-input').value;
    const listItems = document.getElementsByClassName('file-list-item');
    for (let i = 0; i < listItems.length; i++) {
        const listItem = listItems[i];
        if (!searchText || listItem.innerText.indexOf(searchText) > -1) {
            listItem.parentElement.style.display = 'table-row';
        } else {
            listItem.parentElement.style.display = 'none';
        }
    }
}

/**
 * 监听文件拖拽
 */
function onFileDrop() {
    const dropArea = document.getElementById("dropArea");

    window.addEventListener("dragenter", function (e) {
        dropArea.style.display = 'block';
    });

    // 阻止浏览器默认行为
    dropArea.addEventListener("dragover", function (e) {
        e.preventDefault();
        dropArea.classList.add("active");
    });

    // 恢复默认样式
    dropArea.addEventListener("dragleave", function () {
        dropArea.classList.remove("active"); // 移除类名
        dropArea.style.display = 'none';
    });

    // 文件放置时触发
    dropArea.addEventListener("drop", function (e) {
        e.preventDefault();
        dropArea.classList.remove("active"); // 移除类名
        dropArea.style.display = 'none';

        // 获取拖拽的文件
        const files = e.dataTransfer.files;
        uploadFile(files[0]);
    });
}

/**
 * 文件选择时触发
 * @param event
 */
function onFileInputChange(event) {
    console.log('file input change');
    uploadFile(event.target.files[0]);
}

$(function(){
    $('#fileUpload').change(onFileInputChange);

    // 开启文件拖拽
    onFileDrop();

    // 加载开源绘制右键菜单的库
    document.addEventListener('contextmenu', function (e) {
        e.preventDefault();
    });

    feather.replace();

    // 为每个class为list-group-item的元素添加点击事件
    const listItems = document.getElementsByClassName('file-list-item');
    for (let i = 0; i < listItems.length; i++) {
        listItems[i].addEventListener('click', onenFileClick);
        // 如果文件名包含enc，增加属性title为加密文档密码：luoshu
        if (listItems[i].innerText.indexOf('enc') > -1) {
            listItems[i].setAttribute('title', '加密文档密码：luoshu');
        }
    }

    // search-input的enter事件
    document.getElementById('search-input').addEventListener('keyup', function (event) {
        if (event.keyCode === 13) searchFile();
    });
})
