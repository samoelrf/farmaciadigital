@echo off
REM Compila o servidor e os testes (pasta out) e sobe o site e a API na porta 8080.
REM A senha do banco vem da variavel DB_PASSWORD (veja o README da raiz).
cd /d "%~dp0"

if not exist out mkdir out
set FONTES=
for %%f in (src\*.java testes\*.java) do call set "FONTES=%%FONTES%% %%f"

javac -encoding UTF-8 -cp "lib\*" -d out %FONTES%
if errorlevel 1 (
    echo ERRO: a compilacao falhou. Veja as mensagens acima.
    pause
    exit /b 1
)

if "%DB_PASSWORD%"=="" echo AVISO: DB_PASSWORD nao definida. As rotas /api vao dar erro.
java -Dstdout.encoding=UTF-8 -cp "out;lib\*" Servidor
