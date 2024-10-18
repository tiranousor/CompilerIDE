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

function addFileToContainer(fileName) {
    console.log("Добавляем файл:", fileName);
    const filesContainer = document.getElementById('files-container');

    const fileElement = document.createElement('div');
    fileElement.classList.add('file-item-container');

    const fileNameSpan = document.createElement('span');
    fileNameSpan.textContent = fileName;
    fileNameSpan.classList.add('file-name');

    // Обработчик клика по файлу для его открытия
    fileElement.addEventListener('click', () => {
        openFile(fileName);  // Открываем файл в редакторе
        highlightActiveFile(fileElement);  // Подсветка активного файла
    });

    fileElement.appendChild(fileNameSpan);
    filesContainer.appendChild(fileElement);
}

function openFile(fileName) {
    const currentFileName = getCurrentFileName();

    // Сохраняем текущее содержимое открытого файла перед переключением
    if (currentFileName && openFiles[currentFileName]) {
        openFiles[currentFileName] = editor.getValue();
    }

    // Устанавливаем содержимое выбранного файла
    if (openFiles[fileName]) {
        editor.setValue(openFiles[fileName]);
    } else {
        const projectId = window.projectId;  // Используем window.projectId
        fetch(`/projects/${projectId}/files/${fileName}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`Ошибка при загрузке файла: ${response.statusText}`);
                }
                return response.text();
            })
            .then(content => {
                editor.setValue(content);
                openFiles[fileName] = content;
            })
            .catch(error => {
                console.error('Ошибка при загрузке файла:', error);
            });
    }

    highlightActiveFileByName(fileName);
}

function highlightActiveFile(selectedFileElement) {
    const fileItems = document.querySelectorAll('.file-item-container');
    fileItems.forEach(item => item.classList.remove('active'));  // Снимаем выделение

    selectedFileElement.classList.add('active');  // Выделяем активный файл
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

// Добавление файла в массив файлов
function addFile(fileName, content) {
    window.files.push({ fileName, content });
}




// Обновляем текущее имя файла для корректного сохранения
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

            // Добавляем в массив открытых файлов
            openFiles[fileName] = content;

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
function downloadFile() {
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
        // Создаем FormData для отправки файлов
        const formData = new FormData();

        // Добавляем каждый файл в FormData
        files.forEach(file => {
            const blob = new Blob([file.content], { type: 'text/plain' });
            formData.append('files', blob, file.fileName);
        });

        // Если нужен путь, добавьте его
        formData.append('path', ''); // Укажите путь, если требуется

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


// Отправка всех файлов на сервер
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
        const response = await fetch('http://localhost:8080/compile', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken  // Добавляем CSRF-токен в заголовок
            },
            body: JSON.stringify({
                files: files,
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
document.addEventListener('click', function(event) {
    if (event.target.classList.contains('folder-name')) {
        const folder = event.target.parentElement;
        const folderChildren = folder.querySelectorAll('.file-item-container, .folder-item-container');

        folderChildren.forEach(child => {
            child.style.display = (child.style.display === 'none') ? 'block' : 'none';
        });
    }
});
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
                openFiles[filePath] = content;

                // Отображаем файл или папку в контейнере
                addFileToContainer(filePath);
            };
            reader.readAsText(file);
        }
    };

    input.click();
}
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

// Функция для отображения ошибки
function showErrorMessage(message) {
    const messageContainer = document.getElementById('message-container');
    messageContainer.innerHTML = `<div class="alert alert-danger">${message}</div>`;
    setTimeout(() => {
        messageContainer.innerHTML = '';
    }, 5000);
}

