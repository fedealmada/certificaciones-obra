# Sincronizacion segura del proyecto

Esta herramienta genera un backup local de MySQL y crea un commit local con los cambios del proyecto.

## Uso manual

Desde PowerShell:

```powershell
cd "C:\Users\Usuario\Downloads\certificaciones-obra-main (2)\certificaciones-obra-main"
.\tools\backup-commit-local.ps1
```

Despues, para subir a GitHub:

```powershell
git push origin main
```

## Uso automatico con Programador de tareas

Se puede crear una tarea de Windows que ejecute este script cada cierto tiempo, por ejemplo al finalizar el dia.

Programa:

```text
powershell.exe
```

Argumentos:

```text
-ExecutionPolicy Bypass -File "C:\Users\Usuario\Downloads\certificaciones-obra-main (2)\certificaciones-obra-main\tools\backup-commit-local.ps1"
```

## Por que el push no queda automatico por defecto

El backup contiene datos reales de la base. Subirlo automaticamente a GitHub puede exponer informacion sensible si el repositorio no es privado o si alguien accede a la cuenta. Por eso el script deja el commit listo y el push queda como paso consciente.
