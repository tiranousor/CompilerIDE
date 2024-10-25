const templates = {
    "java": `public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, Java!");\n    }\n}`,
    "cpp": `#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << "Hello, C++!" << endl;\n    return 0;\n}`,
    "python3": `print("Hello, Python!")`
};

// Используем window.files для глобальной доступности
// Получаем CSRF-токен и заголовок из метатегов (глобально)
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

// Получение текущего имени файла
function getCurrentFileName() {
    var selectedNodes = $('#jstree').jstree('get_selected', true);
    if (selectedNodes.length > 0 && selectedNodes[0].type === 'file') {
        return selectedNodes[0].id;
    }
    return null;
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
            const projectId = window.projectId;  // Используем window.projectId
            fetch(`/projects/${projectId}/files?path=${encodeURIComponent(filePath)}`)
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

// Функция для скачивания файла по пути
function downloadFileByPath(filePath) {
    const projectId = window.projectId;
    fetch(`/projects/${projectId}/files/download`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [csrfHeader]: csrfToken
        },
        body: JSON.stringify({ path: filePath })
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

// Добавление файла в массив файлов
function addFile(fileName, content) {
    window.files.push({ fileName, content });
    window.openFiles[fileName] = content; // Сохраняем содержимое файла в объект открытых файлов
}

// Загрузка файла в редактор и добавление его в JsTree
function uploadFile() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.txt,.java,.py,.cpp';

    input.onchange = e => {
        const file = e.target.files[0];
        const reader = new FileReader();

        reader.onload = event => {
            const content = event.target.result;
            const fileName = file.name;

            // Добавляем файл в массив
            addFile(fileName, content);

            // Устанавливаем содержимое файла в редактор
            editor.setValue(content);

            // Отправляем файл на сервер
            saveProject();

            // Обновляем дерево файлов
            $('#jstree').jstree('refresh');
        };
        reader.readAsText(file);
    };
    input.click();
}

// Скачивание содержимого текущего файла
function downloadCurrentFile() {
    const content = editor.getValue();
    const blob = new Blob([content], { type: 'text/plain' });

    const link = document.createElement('a');
    link.download = `${getCurrentFileName()}`;
    link.href = window.URL.createObjectURL(blob);
    link.click();
}

// Обновляем функцию saveProject
async function saveProject() {
    const language = document.getElementById('languageSelect').value;

    // Получаем содержимое текущего файла и обновляем его в openFiles
    const currentFileName = getCurrentFileName();
    if (currentFileName) {
        const currentContent = editor.getValue();
        window.openFiles[currentFileName] = currentContent;
    }

    const projectId = window.projectId;

    if (!projectId) {
        alert("Не удалось определить идентификатор проекта.");
        return;
    }

    try {
        // Создаём объект для отправки файлов
        const filesToSave = [];

        // Проходим по всем открытым файлам и добавляем их в массив
        for (const [filePath, content] of Object.entries(window.openFiles)) {
            filesToSave.push({ path: filePath, content: content });
        }

        const response = await fetch(`/projects/${projectId}/save`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify({ files: filesToSave })
        });

        if (response.ok) {
            // Отобразить сообщение об успешном сохранении
            showSuccessMessage("Ваш проект успешно сохранён");
            // Обновляем дерево файлов
            $('#jstree').jstree('refresh');
        } else {
            const errorText = await response.text();
            showErrorMessage(`Ошибка при сохранении проекта: ${errorText}`);
        }
    } catch (error) {
        showErrorMessage(`Ошибка: ${error.message}`);
    }
}

function toggleFilesContainer() {
    const filesContainer = document.getElementById('files-container');
    const containerFluid = document.querySelector('.container-fluid');
    filesContainer.classList.toggle('closed');

    const toggleButtonIcon = document.querySelector('#toggle-button i');
    if (filesContainer.classList.contains('closed')) {
        toggleButtonIcon.classList.remove('fa-caret-left');
        toggleButtonIcon.classList.add('fa-caret-right');
    } else {
        toggleButtonIcon.classList.remove('fa-caret-right');
        toggleButtonIcon.classList.add('fa-caret-left');
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
    }
    window.openFiles[fileName] = newContent;  // Обновляем в массиве открытых файлов
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

// Инициализация JsTree
$(document).ready(function() {
    const projectId = window.projectId;

    // Инициализация JsTree с необходимыми плагинами и контекстным меню
    $('#jstree').jstree({
        'core': {
            'data': {
                'url': `/projects/${projectId}/files`,
                'data': function (node) {
                    return { 'path': node.id };
                }
            },
            'check_callback': true
        },
        'types': {
            'default': {
                'icon': 'fa fa-folder'
            },
            'file': {
                'icon': 'fa fa-file'
            }
        },
        'plugins': ['types', 'contextmenu', 'dnd', 'state', 'unique'],
        'contextmenu': {
            'items': customMenu
        }
    });

    // Обработчик события выбора узла
    $('#jstree').on('select_node.jstree', function(e, data) {
        var node = data.node;
        var filePath = node.id;

        if (node.type === 'file') {
            // Открываем файл
            openFile(filePath);
        } else {
            // Переключаем раскрытие папки
            $('#jstree').jstree('toggle_node', node);
        }
    });

    // Обработчик создания узла
    $('#jstree').on('create_node.jstree', function (e, data) {
        $.ajax({
            type: 'POST',
            url: `/projects/${projectId}/files/create`,
            data: JSON.stringify({
                parent: data.parent,
                text: data.node.text,
                type: data.node.type
            }),
            contentType: 'application/json',
            headers: {
                [csrfHeader]: csrfToken
            },
            success: function (d) {
                data.instance.set_id(data.node, d.id);
                showSuccessMessage("Узел создан успешно");
                // Если это файл, добавляем его в openFiles
                if (data.node.type === 'file') {
                    window.openFiles[d.id] = '';
                }
            },
            error: function (xhr, status, error) {
                data.instance.refresh();
                showErrorMessage("Ошибка при создании узла: " + error);
            }
        });
    });

    // Обработчик переименования узла
    $('#jstree').on('rename_node.jstree', function (e, data) {
        $.ajax({
            type: 'PUT',
            url: `/projects/${projectId}/files/rename`,
            data: JSON.stringify({
                path: data.node.id,
                newName: data.text
            }),
            contentType: 'application/json',
            headers: {
                [csrfHeader]: csrfToken
            },
            success: function (d) {
                showSuccessMessage("Узел переименован успешно");
            },
            error: function (xhr, status, error) {
                data.instance.refresh();
                showErrorMessage("Ошибка при переименовании узла: " + error);
            }
        });
    });

    // Обработчик удаления узла
    $('#jstree').on('delete_node.jstree', function (e, data) {
        $.ajax({
            type: 'DELETE',
            url: `/projects/${projectId}/files/delete`,
            data: JSON.stringify({
                path: data.node.id
            }),
            contentType: 'application/json',
            headers: {
                [csrfHeader]: csrfToken
            },
            success: function (d) {
                showSuccessMessage("Узел удалён успешно");
                // Удаляем файл из openFiles, если это файл
                if (data.node.type === 'file') {
                    delete window.openFiles[data.node.id];
                }
            },
            error: function (xhr, status, error) {
                data.instance.refresh();
                showErrorMessage("Ошибка при удалении узла: " + error);
            }
        });
    });

    // Обработчик перемещения узла
    $('#jstree').on('move_node.jstree', function (e, data) {
        $.ajax({
            type: 'PUT',
            url: `/projects/${projectId}/files/move`,
            data: JSON.stringify({
                path: data.node.id,
                newParent: data.parent,
                oldParent: data.old_parent,
                position: data.position
            }),
            contentType: 'application/json',
            headers: {
                [csrfHeader]: csrfToken
            },
            success: function (d) {
                showSuccessMessage("Узел перемещён успешно");
            },
            error: function (xhr, status, error) {
                data.instance.refresh();
                showErrorMessage("Ошибка при перемещении узла: " + error);
            }
        });
    });
});

function customMenu(node) {
    var tree = $('#jstree').jstree(true);
    var items = {
        'Create': {
            'separator_before': false,
            'separator_after': false,
            'label': 'Создать',
            'action': false,
            'submenu': {
                'create_file': {
                    'separator_before': false,
                    'separator_after': false,
                    'label': 'Файл',
                    'action': function (obj) {
                        var newNode = tree.create_node(node, { type: 'file' });
                        if (newNode) {
                            tree.edit(newNode);
                        }
                    }
                },
                'create_folder': {
                    'separator_before': false,
                    'separator_after': false,
                    'label': 'Папку',
                    'action': function (obj) {
                        var newNode = tree.create_node(node, { type: 'default' });
                        if (newNode) {
                            tree.edit(newNode);
                        }
                    }
                }
            }
        },
        'Rename': {
            'separator_before': false,
            'separator_after': false,
            'label': 'Переименовать',
            'action': function (obj) {
                tree.edit(node);
            }
        },
        'Remove': {
            'separator_before': false,
            'separator_after': false,
            'label': 'Удалить',
            'action': function (obj) {
                tree.delete_node(node);
            }
        }
    };

    if (node.type === 'file') {
        // Убираем возможность создавать внутри файла
        delete items.Create;
    }

    return items;
}


// Загрузка папки и добавление файлов в JsTree
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

                // Добавляем файл в массив файлов
                addFile(filePath, content);

                // Добавляем в массив открытых файлов
                window.openFiles[filePath] = content;
            };
            reader.readAsText(file);
        }

        // Отправляем файлы на сервер после загрузки всех файлов
        saveProject();

        // Обновляем дерево файлов
        $('#jstree').jstree('refresh');
    };

    input.click();
}

function createFolder() {
    var ref = $('#jstree').jstree(true),
        sel = ref.get_selected();
    if (!sel.length) { sel = '#'; } else { sel = sel[0]; }
    sel = ref.create_node(sel, { type: 'default' });
    if (sel) {
        ref.edit(sel);
    }
}

function createFile() {
    var ref = $('#jstree').jstree(true),
        sel = ref.get_selected();
    if (!sel.length) { sel = '#'; } else { sel = sel[0]; }
    sel = ref.create_node(sel, { type: 'file' });
    if (sel) {
        ref.edit(sel);
    }
}

