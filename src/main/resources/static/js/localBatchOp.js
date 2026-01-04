const drive = "local";

// 表单提交时，发送ajax请求，发送所有选中的文件id
function batchDelete(action) {
    const fileIds = [];
    // 获取类型为checkbox且被选中的input标签，不要通过类选择
    $("input[type='checkbox']:checked").each(function () {
        // 值不为空时，将值添加到数组中
        if ($(this).val() !== "") {
            fileIds.push($(this).val());
        }
    });
    sendBatchRes(action, fileIds);
}

// 删除文件名非local-开头的文件
function deleteNonLocalFile() {
    const fileIds = [];
    // 遍历tbody中的每一行,每一行的第一个td标签中input的value是文件ID，后面td标签class是filename的是文件名，获取文件名不是local-开头的文件的id
    $("tbody tr").each(function () {
        const id = $(this).children("td").children("input").val();
        const fileName = $(this).children("td.filename").text();
        if (!fileName.startsWith("local-")) {
            fileIds.push(id);
        }
    });
    sendBatchRes('delete', fileIds);
}

// 发送ajax请求，参数是文件id
function sendBatchRes(action, fileIds) {
    console.log('文件ID列表:', fileIds);
    if (fileIds.length === 0) {
        alert("请至少选择一个文件");
        return;
    }
    // json格式的数据发送ajax请求
    $.ajax({
        url: `/v2/context/file/batchOp/${action}?driveId=${drive}`,
        type: "post",
        contentType: "application/json;charset=utf-8",
        data: JSON.stringify(fileIds),
        dataType: "json",
        success: function (data) {
            // 每行显示data中的一个元素
            alert(data.join("\n"));
            window.location.reload();
        }
    });
}

function selectFiles() {
    // 触发选择文件的input标签
    $("#multiFiles").click();
}

// 上传多个文件
function uploadMultiFiles() {
    const formData = new FormData();
    const files = $("#multiFiles")[0].files;
    if (files.length === 0) {
        alert("请选择文件");
        return;
    }
    for (let i = 0; i < files.length; i++) {
        formData.append("files", files[i]);
    }
    $.ajax({
        url: `/v2/context/file/batchOp/upload?driveId=${drive}`,
        type: "post",
        data: formData,
        contentType: false,
        processData: false,
        success: function (data) {
            alert(data.join("\n"));
            window.location.reload();
        }
    });
}

$(() => {
    // 点击id为fileIds的input标签时，勾选所有的checkbox
    $("#fileIds").click(function () {
        // 类型为checkbox且的input标签，不要通过类选择
        $("input[type='checkbox']").prop("checked", this.checked);
    });
    // multiFiles的change事件，选择文件后，上传文件
    $("#multiFiles").change(function () {
        uploadMultiFiles();
    });
})
