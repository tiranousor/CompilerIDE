const templates = {
    "java": `public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, Java!");\n    }\n}`,
    "cpp": `#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << "Hello, C++!" << endl;\n    return 0;\n}`,
    "python3": `print("Hello, Python!")`
};

let files = [];  // Массив для хранения всех файлов пользователя
let editor;
let models = {}; // Объект для хранения моделей для каждого языка

// Инициализация Monaco Editor
require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.28.1/min/vs' }});
require(['vs/editor/editor.main'], function() {
    // Создаём отдельные модели для каждого языка с начальным шаблоном
    models['java'] = monaco.editor.createModel(templates['java'], 'java');
    models['cpp'] = monaco.editor.createModel(templates['cpp'], 'cpp');
    models['python3'] = monaco.editor.createModel(templates['python3'], 'python');

    // Создаём редактор с моделью Java по умолчанию
    editor = monaco.editor.create(document.getElementById('editor-container'), {
        model: models['java'], // Модель по умолчанию — Java
        theme: 'vs-dark'
    });
});

// Обработчик для смены языка
function setLanguage(language) {
    editor.pushUndoStop();  // Останавливаем запись в историю действий
    const currentModel = models[language];
    editor.setModel(currentModel);  // Переключаемся на модель выбранного языка
    editor.pushUndoStop();  // Включаем запись в историю действий
}

// Добавление файла в массив файлов
function addFile(fileName, content) {
    files.push({ fileName, content });
}

// Отправка всех файлов на сервер
async function submitCode() {
    const language = document.getElementById('languageSelect').value;

    // Обновляем содержимое текущего файла перед отправкой
    const currentFileName = getCurrentFileName();
    const currentContent = editor.getValue();
    updateFileContent(currentFileName, currentContent);

    try {
        const response = await fetch('http://localhost:8080/api/compile', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                files: files,  // Передаем все файлы
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
}

// Получение текущего имени файла
function getCurrentFileName() {
    const model = editor.getModel();
    for (const [language, modelInstance] of Object.entries(models)) {
        if (model === modelInstance) {
            return language + ".java";  // По умолчанию даем имя в формате <язык>.java
        }
    }
}

// Загрузка файлов в редактор и добавление в контейнер файлов
function uploadFile() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.txt,.java,.py,.cpp';  // Принимаем файлы с расширениями .txt, .java, .py, .cpp

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

// Добавление файла в контейнер слева
function addFileToContainer(fileName) {
    const filesContainer = document.getElementById('files-container');

    const fileElement = document.createElement('div');
    fileElement.classList.add('file-item-container');

    const fileNameSpan = document.createElement('span');
    fileNameSpan.textContent = fileName;
    fileNameSpan.classList.add('file-name');

    fileElement.appendChild(fileNameSpan);
    filesContainer.appendChild(fileElement);
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
