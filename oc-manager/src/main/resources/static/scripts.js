const templates = {
    "java": `public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, Java!");\n    }\n}`,
    "cpp": `#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << "Hello, C++!" << endl;\n    return 0;\n}`,
    "python3": `print("Hello, Python!")`
};

let files = [];  // Массив для хранения всех файлов пользователя
let openFiles = {};  // Объект для хранения содержимого всех открытых файлов
let editor;
let models = {}; // Объект для хранения моделей для каждого языка

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
    files.push({ fileName, content });
}

// Открытие файла в редакторе
function openFile(fileName) {
    const currentFileName = getCurrentFileName();

    // Сохраняем текущее содержимое открытого файла перед переключением
    if (currentFileName && openFiles[currentFileName]) {
        openFiles[currentFileName] = editor.getValue();  // Сохраняем код
    }

    // Устанавливаем содержимое выбранного файла
    if (openFiles[fileName]) {
        editor.setValue(openFiles[fileName]);  // Загружаем содержимое
    } else {
        const file = files.find(f => f.fileName === fileName);
        if (file) {
            editor.setValue(file.content);  // Устанавливаем содержимое нового файла
            openFiles[fileName] = file.content;  // Добавляем его в открытые файлы
        }
    }

    updateCurrentFileName(fileName);  // Обновляем активный файл
}

// Обновляем текущее имя файла для корректного сохранения
function updateCurrentFileName(fileName) {
    const fileItems = document.querySelectorAll('.file-item-container .file-name');
    fileItems.forEach(item => {
        if (item.textContent === fileName) {
            item.closest('.file-item-container').classList.add('active');
        } else {
            item.closest('.file-item-container').classList.remove('active');
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

// Добавление файла в контейнер слева и обработка кликов
function addFileToContainer(fileName) {
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

// Подсветка активного файла
function highlightActiveFile(selectedFileElement) {
    const fileItems = document.querySelectorAll('.file-item-container');
    fileItems.forEach(item => item.classList.remove('active'));  // Снимаем выделение

    selectedFileElement.classList.add('active');  // Выделяем активный файл
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
    const file = files.find(file => file.fileName === fileName);
    if (file) {
        file.content = newContent;
    }
    openFiles[fileName] = newContent;  // Обновляем в массиве открытых файлов
}
