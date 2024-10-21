const templates = {
    "java": `public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, Java!");\n    }\n}`,
    "cpp": `#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << "Hello, C++!" << endl;\n    return 0;\n}`,
    "python3": `print("Hello, Python!")`
};

// Глобальные переменные
const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
window.files = [];  // Массив для хранения всех файлов пользователя
window.openFiles = {};  // Объект для хранения содержимого всех открытых файлов
let editor;
let models = {}; // Объект для хранения моделей для каждого языка
const TEXT_FILE_EXTENSIONS = ['.txt', '.java', '.py', '.cpp', '.js', '.html', '.css'];
const BINARY_FILE_EXTENSIONS = ['.class', '.exe', '.png', '.jpg', '.gif', '.pdf'];

// Функция для проверки, является ли файл текстовым
function isTextFile(filePath) {
    return TEXT_FILE_EXTENSIONS.some(ext => filePath.endsWith(ext));
}

// Функция для проверки, является ли файл бинарным
function isBinaryFile(filePath) {
    return BINARY_FILE_EXTENSIONS.some(ext => filePath.endsWith(ext));
}

// Инициализация jsTree
$(function() {
    $('#jstree').jstree({
        'core' : {
            'data' : {
                'url' : function (node) {
                    if(node.id === '#') {
                        return `/projects/${window.projectId}/files?path=`;
                    } else {
                        return `/projects/${window.projectId}/files?path=${encodeURIComponent(node.id)}`;
                    }
                },
                'data' : function (node) {
                    return { 'path' : node.id };
                }
            },
            'check_callback' : true, // Позволяет выполнять операции
            'themes': {
                'responsive': false
            }
        },
        'plugins' : ['contextmenu', 'dnd', 'types', 'search', 'state', 'wholerow', 'unique'],
        'types' : {
            'default' : { 'icon' : 'fas fa-folder' },
            'file' : { 'icon' : 'fas fa-file' }
        },
        'contextmenu': {
            'items': function(node) {
                var tree = $("#jstree").jstree(true);
                return {
                    "Create": {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Создать",
                        "action": function (obj) {
                            tree.create_node(node, {"type":"file"}, "last", function (new_node) {
                                setTimeout(function () { tree.edit(new_node); },0);
                            });
                        }
                    },
                    "Rename": {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Переименовать",
                        "action": function (obj) { tree.edit(node); }
                    },
                    "Remove": {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Удалить",
                        "action": function (obj) { tree.delete_node(node); }
                    }
                };
            }
        }
    })
    .on('create_node.jstree', function (e, data) {
        // Логика создания файла или папки на сервере
        var nodeType = data.node.type; // 'default' для папок, 'file' для файлов

        $.ajax({
            type: 'POST',
            url: `/projects/${window.projectId}/files/create`,
            headers: {
                [csrfHeader]: csrfToken
            },
            contentType: 'application/json',
            data: JSON.stringify({
                'parent': data.parent === '#' ? '' : data.parent,
                'text': data.node.text,
                'type': nodeType // 'default' для папок, 'file' для файлов
            }),
            success: function(response) {
                // Обновление id созданного узла
                data.instance.set_id(data.node, response.id);
                // Добавить файл в глобальный массив, если это файл
                if (response.type === 'file') {
                    window.files.push({ fileName: response.text, content: "" });
                }
            },
            error: function(error) {
                // Обработка ошибки
                $('#jstree').jstree(true).delete_node(data.node);
                alert('Ошибка при создании файла/папки');
            }
        });
    })
    .on('rename_node.jstree', function (e, data) {
        $.ajax({
            type: 'PUT',
            url: `/projects/${window.projectId}/files/rename`,
            headers: {
                [csrfHeader]: csrfToken
            },
            contentType: 'application/json',
            data: JSON.stringify({
                'path': data.node.id,
                'newName': data.text
            }),
            success: function(response) {
                // Успешное переименование
                // Если это файл, обновить имя в глобальном массиве
                if (data.node.type === 'file') {
                    let file = window.files.find(f => f.fileName === data.old);
                    if (file) {
                        file.fileName = data.text;
                    }
                }
            },
            error: function(error) {
                // Обработка ошибки
                $('#jstree').jstree(true).set_text(data.node, data.old);
                alert('Ошибка при переименовании файла/папки');
            }
        });
    })
    .on('delete_node.jstree', function (e, data) {
        $.ajax({
            type: 'DELETE',
            url: `/projects/${window.projectId}/files/delete`,
            headers: {
                [csrfHeader]: csrfToken
            },
            contentType: 'application/json',
            data: JSON.stringify({
                'path': data.node.id
            }),
            success: function(response) {
                // Успешное удаление
                // Если это файл, удалить из глобального массива
                if (data.node.type === 'file') {
                    window.files = window.files.filter(f => f.fileName !== data.node.text);
                }
            },
            error: function(error) {
                // Обработка ошибки
                $('#jstree').jstree(true).refresh();
                alert('Ошибка при удалении файла/папки');
            }
        });
    })
    .on('select_node.jstree', function (e, data) {
        // Логика открытия файла в редакторе при выборе узла
        const selectedNode = data.node;
        if(selectedNode.type === 'file') {
            openFile(selectedNode.id);
        }
    });
});

// Инициализация Monaco Editor
require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.28.1/min/vs' }});
require(['vs/editor/editor.main'], function() {
    // Создаём модели для каждого языка с шаблонами
    models['java'] = monaco.editor.createModel(templates['java'], 'java');
    models['cpp'] = monaco.editor.createModel(templates['cpp'], 'cpp');
    models['python3'] = monaco.editor.createModel(templates['python3'], 'python');

    // Инициализация редактора с моделью Java по умолчанию
    editor = monaco.editor.create(document.getElementById('editor-container'), {
        model: models['java'],
        theme: 'vs-dark'
    });

    // Добавляем обработчик для увеличения/уменьшения размера шрифта с помощью горячих клавиш
    let currentFontSize = 14; // начальный размер шрифта
    window.addEventListener('keydown', function(event) {
        if ((event.ctrlKey || event.metaKey) && editor.hasTextFocus()) {
            if (event.key === '+') {
                event.preventDefault();
                currentFontSize += 1;
                editor.updateOptions({ fontSize: currentFontSize });
            } else if (event.key === '-') {
                event.preventDefault();
                currentFontSize -= 1;
                editor.updateOptions({ fontSize: currentFontSize });
            }
        }
    });
});

// Обработчик для смены языка
function setLanguage(language) {
    editor.pushUndoStop();  // Останавливаем запись в историю
    const currentModel = models[language];
    editor.setModel(currentModel);  // Переключаем модель
    editor.pushUndoStop();  // Включаем запись в историю
}

// Функция для открытия файла
function openFile(filePath) {
    const currentFileName = getCurrentFileName();

    // Сохраняем текущее содержимое открытого файла перед переключением
    if (currentFileName && openFiles[currentFileName]) {
        openFiles[currentFileName] = editor.getValue();
    }

    if (isBinaryFile(filePath)) {
        // Для бинарных файлов предоставляем возможность скачать их
        downloadFileByPath(filePath);
        return;
    }

    if (isTextFile(filePath)) {
        // Устанавливаем содержимое выбранного файла
        if (openFiles[filePath]) {
            editor.setValue(openFiles[filePath]);
        } else {
            fetch(`/projects/${window.projectId}/files?path=${encodeURIComponent(filePath)}`)
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`Ошибка при загрузке файла: ${response.statusText}`);
                    }
                    return response.text();
                })
                .then(content => {
                    editor.setValue(content);
                    openFiles[filePath] = content;
                })
                .catch(error => {
                    console.error('Ошибка при загрузке файла:', error);
                    showErrorMessage(`Ошибка при загрузке файла: ${filePath}`);
                });
        }
    } else {
        // Если тип файла неизвестен, можно показать сообщение или предложить скачать
        showErrorMessage(`Неподдерживаемый тип файла: ${filePath}`);
    }
}

// Получение текущего имени файла
function getCurrentFileName() {
    return editor.getModel()?.uri.path.split('/').pop() || null;
}

// Функция для скачивания файла по пути
function downloadFileByPath(filePath) {
    const projectId = window.projectId;
    fetch(`/projects/${projectId}/files/download`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [document.querySelector('meta[name="_csrf_header"]').getAttribute('content')]: document.querySelector('meta[name="_csrf"]').getAttribute('content')
        },
        body: JSON.stringify({ 'path': filePath })
    })
    .then(response => {
        if (!response.ok) {
            throw new Error(`Ошибка при скачивании файла: ${response.statusText}`);
        }
        return response.blob();
    })
    .then(blob => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filePath.split('/').pop(); // Извлекаем имя файла
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
    })
    .catch(error => {
        console.error('Ошибка при скачивании файла:', error);
        showErrorMessage(`Ошибка при скачивании файла: ${filePath}`);
    });
}

// Функция для сохранения проекта
async function saveProject() {
    const language = document.getElementById('languageSelect').value;

    // Получаем содержимое текущего файла и обновляем его в массиве файлов
    const currentFileName = getCurrentFileName();
    const currentContent = editor.getValue();
    updateFileContent(currentFileName, currentContent);

    const projectId = window.projectId;

    if (!projectId) {
        alert("Не удалось определить идентификатор проекта.");
        return;
    }

    try {
        // Создаём FormData для отправки файлов
        const formData = new FormData();

        // Добавляем каждый файл в FormData
        window.files.forEach(file => {
            // Определяем тип файла
            let mimeType = 'text/plain';
            if (isBinaryFile(file.fileName)) {
                mimeType = 'application/octet-stream';
            }

            const blob = new Blob([file.content], { type: mimeType });
            formData.append('files', blob, file.fileName);
        });

        // Если нужен путь, добавьте его
        formData.append('basePath', ''); // Укажите путь, если требуется

        const response = await fetch(`/projects/${projectId}/save`, {
            method: 'POST',
            headers: {
                [csrfHeader]: csrfToken
            },
            body: formData
        });

        if (response.ok) {
            // Отобразить сообщение об успешном сохранении
            showSuccessMessage("Ваш проект успешно сохранён");
        } else {
            const errorText = await response.text();
            showErrorMessage(`Ошибка при сохранении проекта: ${errorText}`);
        }
    } catch (error) {
        showErrorMessage(`Ошибка: ${error.message}`);
    }
}

// Отправка всех файлов на сервер для компиляции
async function submitCode() {
    const language = document.getElementById('languageSelect').value;

    // Обновляем содержимое текущего файла перед отправкой
    const currentFileName = getCurrentFileName();
    const currentContent = editor.getValue();
    updateFileContent(currentFileName, currentContent);

    // Получаем CSRF-токен и заголовок из метатегов
    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    try {
        const response = await fetch(`/projects/${window.projectId}/compile`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken  // Добавляем CSRF-токен в заголовок
            },
            body: JSON.stringify({
                files: window.files,
                language: language
            })
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        const result = await response.text();
        document.getElementById('output').textContent = result || "No errors, code executed successfully!";
    } catch (error) {
        document.getElementById('output').textContent = `Error: ${error.message}`;
    }
}

// Обновляем содержимое файла в массиве файлов
function updateFileContent(fileName, newContent) {
    const file = window.files.find(file => file.fileName === fileName);
    if (file) {
        file.content = newContent;
    } else {
        // Если файла нет, добавляем его
        window.files.push({ fileName: fileName, content: newContent });
    }
    window.openFiles[fileName] = newContent;  // Обновляем в объекте открытых файлов
}

// Функция для отображения успешного сообщения
function showSuccessMessage(message) {
    const messageContainer = document.getElementById('message-container');
    messageContainer.innerHTML = `<div class="alert alert-success">${message}</div>`;
    setTimeout(() => {
        messageContainer.innerHTML = '';
    }, 5000); // Сообщение исчезнет через 5 секунд
}

// Функция для отображения ошибки
function showErrorMessage(message) {
    const messageContainer = document.getElementById('message-container');
    messageContainer.innerHTML = `<div class="alert alert-danger">${message}</div>`;
    setTimeout(() => {
        messageContainer.innerHTML = '';
    }, 5000);
}

// Функция для скачивания содержимого текущего файла
function downloadCurrentFile() {
    const currentFileName = getCurrentFileName();
    if (currentFileName) {
        downloadFileByPath(currentFileName);
    } else {
        showErrorMessage("Нет открытого файла для скачивания");
    }
}

// Переключение панели файлов
function toggleFilesContainer() {
    const filesContainer = document.getElementById('files-container');
    const toggleButton = document.getElementById('toggle-button');
    const bodyElement = document.body;

    filesContainer.classList.toggle('open');
    if (filesContainer.classList.contains('open')) {
        bodyElement.style.marginLeft = '320px'; // Сжимаем весь body, когда панель открыта
        toggleButton.querySelector('i').classList.remove('fa-caret-right');
        toggleButton.querySelector('i').classList.add('fa-caret-left');
    } else {
        bodyElement.style.marginLeft = '0'; // Возвращаем body на место, когда панель закрыта
        toggleButton.querySelector('i').classList.remove('fa-caret-left');
        toggleButton.querySelector('i').classList.add('fa-caret-right');
    }
}

// Загрузка файла и добавление его в jsTree
function uploadFile() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.txt,.java,.py,.cpp';

    input.onchange = e => {
        const file = e.target.files[0];
        if (!file) return;

        const reader = new FileReader();

        reader.onload = event => {
            const content = event.target.result;
            const fileName = file.name;

            // Добавляем файл в глобальный массив
            window.files.push({ fileName, content });
            window.openFiles[fileName] = content;

            // Добавляем файл в jsTree
            $('#jstree').jstree(true).create_node('#', { "text": fileName, "type": "file" }, "last", function(new_node) {
                $('#jstree').jstree(true).open_node(new_node);
            });

            // Открываем файл в редакторе
            openFile(fileName);
        };
        reader.readAsText(file);
    };
    input.click();
}

// Загрузка папки и добавление файлов в jsTree
function uploadFolder() {
    const input = document.createElement('input');
    input.type = 'file';
    input.webkitdirectory = true;
    input.multiple = true;

    input.onchange = async (event) => {
        const files = event.target.files;

        for (let file of files) {
            const reader = new FileReader();
            reader.onload = function (e) {
                const content = e.target.result;
                const filePath = file.webkitRelativePath;  // Это путь к файлу внутри папки

                // Добавляем файл в глобальный массив
                window.files.push({ fileName: filePath, content: content });
                window.openFiles[filePath] = content;

                // Добавляем в jsTree
                const pathParts = filePath.split('/');
                let parent = '#';
                for (let i = 0; i < pathParts.length - 1; i++) {
                    const folderPath = pathParts.slice(0, i + 1).join('/');
                    // Проверяем, существует ли папка в jsTree
                    if (!$('#jstree').jstree(true).get_node(folderPath)) {
                        $('#jstree').jstree(true).create_node(parent, { "text": pathParts[i], "type": "default", "id": folderPath }, "last");
                    }
                    parent = folderPath;
                }
                const fileName = pathParts[pathParts.length - 1];
                $('#jstree').jstree(true).create_node(parent, { "text": fileName, "type": "file" }, "last");

                // Открываем файл в редакторе
                openFile(filePath);
            };
            reader.readAsText(file);
        }
    };

    input.click();
}
