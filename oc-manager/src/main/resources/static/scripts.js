const templates = {
    "java": `public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, Java!");\n    }\n}`,
    "cpp": `#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << "Hello, C++!" << endl;\n    return 0;\n}`,
    "python3": `print("Hello, Python!")`
};

// Используем window.files для глобальной доступности
window.files = [];  // Массив для хранения всех файлов пользователя
window.openFiles = {};  // Объект для хранения содержимого всех открытых файлов
let editor;
let models = {}; // Объект для хранения моделей для каждого языка

let selectedFilePath = null;

// Функция для добавления файла или папки в контейнер с файлами
function addFileToContainer(filePath) {
    const filesContainer = document.getElementById('files-container');
    const pathParts = filePath.split('/'); // Разбиваем путь на части (папки и файл)
    let currentElement = filesContainer;

    pathParts.forEach((part, index) => {
        if (index === pathParts.length - 1) {
            // Это файл, добавляем его как элемент
            const fileElement = document.createElement('div');
            fileElement.classList.add('file-item-container');

            const fileNameSpan = document.createElement('span');
            fileNameSpan.textContent = part;  // Только имя файла
            fileNameSpan.classList.add('file-name');

            // Обработчик клика для открытия файла
            fileElement.addEventListener('click', () => {
                openFile(filePath);  // Открываем файл в редакторе
                highlightActiveFile(fileElement);  // Подсвечиваем активный файл
            });

            // Обработчик правого клика для отображения контекстного меню
            fileElement.addEventListener('contextmenu', (event) => {
                showContextMenu(event, filePath);
            });

            fileElement.appendChild(fileNameSpan);
            currentElement.appendChild(fileElement);
        } else {
            // Это папка, создаем ее, если она еще не создана
            let folderElement = currentElement.querySelector(`[data-folder="${part}"]`);

            if (!folderElement) {
                folderElement = document.createElement('div');
                folderElement.classList.add('folder-item-container');
                folderElement.setAttribute('data-folder', part); // Сохраняем идентификатор папки

                // Добавляем заголовок папки
                const folderNameSpan = document.createElement('span');
                folderNameSpan.textContent = part;  // Имя папки
                folderNameSpan.classList.add('folder-name');
                folderNameSpan.style.fontWeight = 'bold';  // Выделяем папку жирным

                // Обработчик клика для сворачивания/разворачивания папки
                folderNameSpan.addEventListener('click', function () {
                    const isOpen = folderElement.classList.toggle('open');
                    const folderChildren = folderElement.querySelectorAll('.file-item-container, .folder-item-container');
                    folderChildren.forEach(child => {
                        child.style.display = isOpen ? 'block' : 'none';
                    });
                });

                // Обработчик правого клика для папок
                folderElement.addEventListener('contextmenu', (event) => {
                    showContextMenu(event, filePath);
                });

                // Добавляем папку в текущий контейнер
                folderElement.appendChild(folderNameSpan);
                currentElement.appendChild(folderElement);
            }

            // Продолжаем строить структуру внутри этой папки
            currentElement = folderElement;
        }
    });
}

// Функция для отображения контекстного меню
function showContextMenu(event, filePath) {
    event.preventDefault();
    selectedFilePath = filePath;

    const contextMenu = document.getElementById('context-menu');
    contextMenu.style.top = `${event.clientY}px`;
    contextMenu.style.left = `${event.clientX}px`;
    contextMenu.style.display = 'block';
}

// Скрыть контекстное меню при клике вне его
document.addEventListener('click', function(event) {
    const contextMenu = document.getElementById('context-menu');
    if (!contextMenu.contains(event.target)) {
        contextMenu.style.display = 'none';
    }
});

// Обработчики для опций контекстного меню
document.getElementById('rename-option').addEventListener('click', function() {
    if (selectedFilePath) {
        const newName = prompt("Введите новое имя файла или папки:", selectedFilePath.split('/').pop());
        if (newName && newName.trim() !== "") {
            renameFile(selectedFilePath, newName.trim());
        }
    }
    document.getElementById('context-menu').style.display = 'none';
});

document.getElementById('delete-option').addEventListener('click', function() {
    if (selectedFilePath) {
        const confirmDelete = confirm(`Вы уверены, что хотите удалить "${selectedFilePath.split('/').pop()}"?`);
        if (confirmDelete) {
            deleteFile(selectedFilePath);
        }
    }
    document.getElementById('context-menu').style.display = 'none';
});

// Функция для переименования файла или папки
function renameFile(oldPath, newName) {
    const projectId = window.projectId;
    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    // Определяем новый путь
    const pathParts = oldPath.split('/');
    pathParts.pop(); // Удаляем старое имя файла или папки
    const newPath = pathParts.length > 0 ? pathParts.join('/') + '/' + newName : newName;

    fetch(`/projects/${projectId}/rename`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [csrfHeader]: csrfToken
        },
        body: JSON.stringify({
            oldPath: oldPath,
            newPath: newPath
        })
    })
    .then(response => {
        if (response.ok) {
            // Обновляем локальные данные
            if (window.files.includes(oldPath)) {
                const index = window.files.indexOf(oldPath);
                window.files[index] = newPath;
            }

            if (openFiles[oldPath]) {
                openFiles[newPath] = openFiles[oldPath];
                delete openFiles[oldPath];
            }

            // Обновляем UI
            refreshFileContainer();
            showSuccessMessage("Файл успешно переименован");
        } else {
            response.text().then(text => {
                showErrorMessage(`Ошибка при переименовании файла: ${text}`);
            });
        }
    })
    .catch(error => {
        console.error('Ошибка при переименовании файла:', error);
        showErrorMessage(`Ошибка при переименовании файла: ${error.message}`);
    });
}

// Функция для удаления файла или папки
function deleteFile(filePath) {
    const projectId = window.projectId;
    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    fetch(`/projects/${projectId}/delete`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [csrfHeader]: csrfToken // Исправлено: передаём правильный заголовок
        },
        body: JSON.stringify({
            filePath: filePath
        })
    })
    .then(response => {
        if (response.ok) {
            // Удаляем из локальных данных
            window.files = window.files.filter(path => path !== filePath && !path.startsWith(filePath + '/'));
            delete openFiles[filePath];

            // Обновляем UI
            refreshFileContainer();
            showSuccessMessage("Файл успешно удалён");
        } else {
            response.text().then(text => {
                showErrorMessage(`Ошибка при удалении файла: ${text}`);
            });
        }
    })
    .catch(error => {
        console.error('Ошибка при удалении файла:', error);
        showErrorMessage(`Ошибка при удалении файла: ${error.message}`);
    });
}

// Функция для обновления контейнера файлов после изменений
function refreshFileContainer() {
    const filesContainer = document.getElementById('files-container');
    filesContainer.innerHTML = '<p>Ваши файлы</p>';
    window.files.forEach(filePath => addFileToContainer(filePath));
}

// Подсветка активного файла
function highlightActiveFileByName(fileName) {
    const fileItems = document.querySelectorAll('.file-item-container');
    fileItems.forEach(item => {
        const itemName = item.querySelector('.file-name').textContent;
        if (itemName === fileName.split('/').pop()) {  // Проверка по последней части пути
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });
}

// Получение имени текущего активного файла
function getCurrentFileName() {
    const activeItem = document.querySelector('.file-item-container.active .file-name');
    return activeItem ? activeItem.textContent : null;
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
});

// Обработчик для смены языка
function setLanguage(language) {
    editor.pushUndoStop();  // Останавливаем запись в историю
    const currentModel = models[language];
    editor.setModel(currentModel);  // Переключаем модель
    editor.pushUndoStop();  // Включаем запись в историю
}

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

                // Добавляем файл в массив файлов как строку пути
                window.files.push(filePath);

                // Добавляем содержимое файла в openFiles
                openFiles[filePath] = content;

                // Отображаем файл или папку в контейнере
                addFileToContainer(filePath);
            };
            reader.readAsText(file);
        }
    };
    input.click();
}

// Добавление файла в массив файлов
function addFile(fileName, content) {
    window.files.push({ fileName, content });
}

// Скачивание содержимого текущего файла
function downloadFile() {
    const content = editor.getValue();
    const blob = new Blob([content], { type: 'text/plain' });
    const link = document.createElement('a');
    link.download = `${getCurrentFileName()}`;
    link.href = window.URL.createObjectURL(blob);
    link.click();
}

async function saveProject() {
    const language = document.getElementById('languageSelect').value;
    const currentFileName = getCurrentFileName();
    const currentContent = editor.getValue();
    updateFileContent(currentFileName, currentContent);

    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
    const projectId = window.projectId;

    if (!projectId) {
        alert("Не удалось определить идентификатор проекта.");
        return;
    }

    try {
        const formData = new FormData();
        window.files.forEach(filePath => {
            const content = openFiles[filePath] || '';
            const blob = new Blob([content], { type: 'text/plain' });
            formData.append('files', blob, filePath);
        });

        const response = await fetch(`/projects/${projectId}/save`, {
            method: 'POST',
            headers: {
                [csrfHeader]: csrfToken
            },
            body: formData
        });

        if (response.ok) {
            showSuccessMessage("Ваш проект успешно сохранён");
        } else {
            const errorText = await response.text();
            showErrorMessage(`Ошибка при сохранении проекта: ${errorText}`);
        }
    } catch (error) {
        showErrorMessage(`Ошибка: ${error.message}`);
    }
}

// Обновляем содержимое файла в массиве файлов
function updateFileContent(fileName, newContent) {
    const file = window.files.find(file => file === fileName); // Исправлено: поиск по строке
    if (file) {
        const index = window.files.indexOf(file);
        window.files[index] = fileName; // Путь остается тем же
        openFiles[fileName] = newContent;  // Обновляем содержимое
    }
}

// Функция для отображения успешного сообщения
function showSuccessMessage(message) {
    const messageContainer = document.getElementById('message-container');
    if (messageContainer) {
        messageContainer.innerHTML = `<div class="alert alert-success">${message}</div>`;
        setTimeout(() => {
            messageContainer.innerHTML = '';
        }, 5000); // Сообщение исчезнет через 5 секунд
    }
}

// Функция для отображения ошибки
function showErrorMessage(message) {
    const messageContainer = document.getElementById('message-container');
    if (messageContainer) {
        messageContainer.innerHTML = `<div class="alert alert-danger">${message}</div>`;
        setTimeout(() => {
            messageContainer.innerHTML = '';
        }, 5000);
    }
}

// Функция для открытия файла в редакторе
function openFile(filePath) {
    const currentFileName = getCurrentFileName();

    // Сохраняем текущее содержимое открытого файла перед переключением
    if (currentFileName && openFiles[currentFileName]) {
        openFiles[currentFileName] = editor.getValue();
    }

    // Устанавливаем содержимое выбранного файла
    if (openFiles[filePath]) {
        editor.setValue(openFiles[filePath]);
    } else {
        const projectId = window.projectId;  // Используем window.projectId
        fetch(`/projects/${projectId}/files/${filePath}`)
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
            });
    }

    highlightActiveFileByName(filePath);
}

// Функция для обновления контейнера файлов после изменений
function refreshFileContainer() {
    const filesContainer = document.getElementById('files-container');
    filesContainer.innerHTML = '<p>Ваши файлы</p>';
    window.files.forEach(filePath => addFileToContainer(filePath));
}
