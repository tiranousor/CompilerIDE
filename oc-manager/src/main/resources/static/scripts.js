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

// Функция для добавления файла или папки в контейнер файлов
function addFileToContainer(filePath) {
    const filesContainer = document.getElementById('files-container');

    const pathParts = filePath.split('/'); // Разбиваем путь на части (папки и файл)
    let currentElement = filesContainer;

    pathParts.forEach((part, index) => {
        if (index === pathParts.length - 1) {
            // Определяем тип файла
            let fileTypeClass = 'unknown';
            if (isTextFile(filePath)) {
                fileTypeClass = 'text';
            } else if (isBinaryFile(filePath)) {
                fileTypeClass = 'binary';
            }

            // Создаём элемент файла
            const fileElement = document.createElement('div');
            fileElement.classList.add('file-item-container', fileTypeClass);

            const fileNameSpan = document.createElement('span');
            fileNameSpan.textContent = part; // Только имя файла
            fileNameSpan.classList.add('file-name');

            // Обработчик клика для открытия или скачивания файла
            fileElement.addEventListener('click', () => {
                if (isBinaryFile(filePath)) {
                    downloadFileByPath(filePath);  // Скачиваем бинарный файл
                } else {
                    openFile(filePath);  // Открываем текстовый файл в редакторе
                }
                highlightActiveFile(fileElement);  // Подсвечиваем активный файл
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

                // Добавляем стрелочку перед папкой
                const arrowIcon = document.createElement('i');
                arrowIcon.classList.add('fas', 'fa-caret-right', 'folder-arrow');

                // Добавляем заголовок папки
                const folderNameSpan = document.createElement('span');
                folderNameSpan.textContent = part;  // Имя папки
                folderNameSpan.classList.add('folder-name');
                folderNameSpan.style.fontWeight = 'bold';  // Выделяем папку жирным

                // Добавляем стрелку и папку в текущий контейнер
                folderElement.appendChild(arrowIcon);
                folderElement.appendChild(folderNameSpan);
                currentElement.appendChild(folderElement);
            }

            // Продолжаем строить структуру внутри этой папки
            currentElement = folderElement;
        }
    });
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
        highlightActiveFileByName(filePath);
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

        highlightActiveFileByName(filePath);
    } else {
        // Если тип файла неизвестен, можно показать сообщение или предложить скачать
        showErrorMessage(`Неподдерживаемый тип файла: ${filePath}`);
    }
}

// Функция для подсветки активного файла по элементу
function highlightActiveFile(selectedFileElement) {
    const fileItems = document.querySelectorAll('.file-item-container');
    fileItems.forEach(item => item.classList.remove('active'));  // Снимаем выделение

    selectedFileElement.classList.add('active');  // Выделяем активный файл
}

// Функция для подсветки активного файла по имени
function highlightActiveFileByName(fileName) {
    const fileItems = document.querySelectorAll('.file-item-container');
    fileItems.forEach(item => {
        const itemName = item.querySelector('.file-name').textContent;
        if (itemName === fileName) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });
}

// Получение текущего имени файла
function getCurrentFileName() {
    return document.querySelector('.file-item-container.active .file-name')?.textContent || null;
}

// Функция для скачивания файла по пути
function downloadFileByPath(filePath) {
    const projectId = window.projectId;
    fetch(`/projects/${projectId}/files?path=${encodeURIComponent(filePath)}`, {
        method: 'GET',
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

// Загрузка файла в редактор и добавление его в контейнер файлов
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

            // Отображаем файл в контейнере файлов
            addFileToContainer(fileName);
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

// Функция для сохранения проекта
async function saveProject() {
    const language = document.getElementById('languageSelect').value;

    // Получаем содержимое текущего файла и обновляем его в массиве файлов
    const currentFileName = getCurrentFileName();
    const currentContent = editor.getValue();
    updateFileContent(currentFileName, currentContent);

    // Получаем CSRF-токен и заголовок из метатегов
    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

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
        const response = await fetch(`/projects/${window.projectId}/compile`, { // Исправлено URL
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

// Обработчик клика для папок (открытие/закрытие)
document.addEventListener('click', function(event) {
    if (event.target.classList.contains('folder-name') || event.target.classList.contains('folder-arrow')) {
        const folderElement = event.target.closest('.folder-item-container');  // Получаем элемент папки
        const arrowElement = folderElement.querySelector('.folder-arrow');  // Получаем стрелочку
        const folderChildren = folderElement.querySelectorAll('.file-item-container, .folder-item-container');

        // Переключаем видимость дочерних элементов (файлов и папок)
        folderChildren.forEach(child => {
            child.style.display = (child.style.display === 'none') ? 'block' : 'none';
        });

        // Переключаем состояние стрелочки и иконки
        arrowElement.classList.toggle('open');  // Переключаем класс open для стрелочки
        folderElement.classList.toggle('open');  // Переключаем класс open для папки
    }
});

// Загрузка папки и добавление файлов в контейнер
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

                // Отображаем файл или папку в контейнере файлов
                addFileToContainer(filePath);
            };
            reader.readAsText(file);
        }
    };

    input.click();
}
