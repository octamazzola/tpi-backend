# Contexto del entorno de desarrollo (WSL2 + Docker) para el proyecto de microservicios

> Este archivo resume la configuración del entorno que ya está hecha y las decisiones tomadas.
> Está pensado para que un asistente de IA (Antigravity) tenga el contexto completo y pueda continuar el trabajo sin repetir pasos.

---

## 1. El proyecto

- **Qué es:** proyecto de backend con **dos microservicios en Java**, que tienen que correr con **Docker**.
- **Contexto:** trabajo académico. El profesor recomendó usar Linux porque Docker en Windows puede consumir mucho hardware.
- **Estado del código:** ya existe una carpeta con el proyecto en Windows. Se venía trabajando desde Visual Studio / VS Code y Antigravity.
- **Dato todavía no definido:** versión de Java que pide el profesor o el proyecto (probablemente 17 o 21). Hay que confirmarlo antes de instalar el JDK.
- **Dato todavía no definido:** si el proyecto ya tiene `Dockerfile` y `docker-compose.yml`, y si incluye base de datos (Postgres, MySQL) o mensajería (Kafka, RabbitMQ).

## 2. Máquina del usuario

| Dato | Valor |
|---|---|
| Sistema base | Windows 11 (compilación 10.0.26100) |
| RAM | 16 GB |
| Usuario en Ubuntu | `octavio` |
| Nombre del equipo | `DESKTOP-ADBVU73` |
| Idioma | Español (Argentina). Preferir explicaciones en español rioplatense. |

## 3. Decisión tomada: no pasarse a Linux, usar WSL2

Con 16 GB de RAM no hace falta hacer dual boot ni reemplazar Windows para este proyecto. Se eligió:

**WSL2 + Ubuntu + Docker Engine instalado directamente en Ubuntu, sin Docker Desktop.**

Motivos:
- Es más liviano que Docker Desktop.
- Se comporta como Linux real, que es lo que recomendó el profesor.
- No requiere tocar la partición del disco ni arriesgar el sistema.
- Si más adelante la máquina se queda corta, se puede evaluar dual boot sin haber perdido nada.

## 4. Lo que YA está instalado y verificado

| Componente | Estado | Detalle |
|---|---|---|
| WSL | Instalado | Subsistema de Windows para Linux **3.0.3** |
| Ubuntu | Instalado y funcionando | **Ubuntu 26.04.1 LTS**, kernel `6.18.54.1-microsoft-standard-WSL2` |
| systemd | Presente y funcionando | `systemd 259`. `systemctl enable --now docker` se ejecutó sin errores. |
| Docker Engine | Instalado y verificado | Repositorio oficial de Docker. `docker run hello-world` funcionó. |
| Docker Compose | Instalado | Plugin `docker compose`, versión **5.6.1** |
| Permisos | Resueltos | Usuario `octavio` en el grupo `docker`. Se usa Docker **sin `sudo`** (se reinició WSL con `wsl --shutdown` para aplicar el grupo). |

### Comandos clave que se usaron (por si hay que repetir la instalación)

**En PowerShell como administrador:**

```powershell
wsl --install -d Ubuntu
wsl -l -v          # verificar que Ubuntu aparezca con VERSION 2
wsl --shutdown     # reiniciar WSL (necesario tras cambios de grupo o de configuración)
```

La instalación se hizo en dos etapas: primero se instala WSL y se activan funciones de Windows, **hay que reiniciar la PC**, y recién entonces se instala/abre Ubuntu y se crea el usuario.

**En Ubuntu, instalación de Docker Engine (formato actual para Ubuntu 26.04, codename `resolute`):**

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo usermod -aG docker $USER
sudo systemctl enable --now docker
```

Docker Engine soporta oficialmente Ubuntu 26.04, así que este método es válido para la versión instalada.

## 5. Lo que FALTA hacer (próximos pasos)

1. **Confirmar la versión de Java** que requiere el proyecto.
2. **Instalar JDK y Maven en Ubuntu** (ejemplo con Java 21; cambiar el número según corresponda):

   ```bash
   sudo apt update
   sudo apt install -y openjdk-21-jdk maven
   java -version
   mvn -version
   ```

   Si algún paquete no existe en Ubuntu 26.04, buscar una alternativa (por ejemplo SDKMAN) en lugar de asumir disponibilidad.
3. **Mover o clonar el proyecto dentro del sistema de archivos de Ubuntu** (ver sección 6).
4. **Revisar o crear** los `Dockerfile` de cada microservicio y un `docker-compose.yml` que los levante juntos.
5. **Probar** `docker compose up --build` y verificar que ambos servicios arranquen y se comuniquen.

## 6. Dónde guardar el proyecto (recomendación importante)

**Recomendado: dentro de Ubuntu**, por ejemplo en `~/proyectos/`. Los builds de Docker y Maven son bastante más rápidos que si los archivos están en `C:\`, porque el acceso entre Windows y WSL2 es lento.

```bash
mkdir -p ~/proyectos
cp -r "/mnt/c/Users/TU_USUARIO/ruta/a/tu-proyecto" ~/proyectos/
cd ~/proyectos/tu-proyecto
```

Si el proyecto está en GitHub, es más limpio usar `git clone` directamente en `~/proyectos`.

**Regla:** una vez copiado, editar **solo la copia de Ubuntu** y dejar la de `C:\` como respaldo, para no tener dos versiones distintas.

**Alternativa (más lenta, sirve para probar rápido):** dejar el proyecto en `C:\` y usarlo desde Ubuntu en `/mnt/c/...`.

## 7. Editores

- **VS Code (recomendado):** instalar la extensión **WSL** de Microsoft. Desde la carpeta del proyecto en Ubuntu, ejecutar `code .`. Se abre en Windows pero trabaja sobre los archivos de Linux.
- **Antigravity:** tiene su propia extensión de WSL (`google.antigravity-remote-wsl`), pero se encontraron reportes de usuarios con la integración **rota o inestable en versiones 2.x**, sobre todo después de actualizaciones (errores del servidor remoto, carpeta "no existe", etc.). Recomendación: **no depender de ella mientras se arma el entorno**. Si se quiere probar, se puede abrir la carpeta desde `\\wsl$\Ubuntu\home\octavio\proyectos\tu-proyecto`.
- **Visual Studio (el violeta, no VS Code):** no es ideal para Java. Si el usuario se refería a este, evaluar alternativas (IntelliJ IDEA o VS Code).
- **IntelliJ IDEA:** se puede abrir el proyecto desde `\\wsl$\Ubuntu\home\octavio\proyectos`.

## 8. Opcional: limitar la RAM que usa WSL

Con 16 GB, dedicar 8 GB a WSL suele ser un buen equilibrio. Crear `C:\Users\TU_USUARIO\.wslconfig`:

```ini
[wsl2]
memory=8GB
processors=4
```

Aplicar con `wsl --shutdown` en PowerShell. También se puede ajustar desde la app de WSL (sección "Configuración").

## 9. Cosas que se decidió NO usar (y por qué)

- **Docker Desktop:** se evitó para ahorrar recursos. Si está instalado, desinstalarlo o desactivar su integración con WSL para que no se pise con Docker Engine.
- **WSL Containers (`wslc.exe`):** es una función nueva que viene con WSL 3.x y trae un CLI parecido a Docker. **No usarla para este proyecto**: el profesor espera Docker estándar, los `Dockerfile` y `docker-compose.yml` son el formato de referencia, y WSLc es muy reciente y no reemplaza del todo a Docker ni a `docker compose`. Se puede experimentar con ella después de entregar el proyecto.

## 10. Notas para el asistente (Antigravity)

- Responder en **español rioplatense**, con un tono claro y paso a paso. El usuario está aprendiendo.
- El usuario **no tiene todavía** claro si el proyecto ya tiene `Dockerfile` ni `docker-compose.yml`. Pedirle la estructura de carpetas de los dos microservicios antes de generar nada.
- No asumir versiones de Java, Spring Boot ni de base de datos. Confirmarlas primero.
- Los comandos se ejecutan **dentro de Ubuntu (WSL)**, no en PowerShell, salvo los que empiezan con `wsl`.
- Problema ya resuelto, por si reaparece: `permission denied ... /var/run/docker.sock` ocurre cuando el grupo `docker` no se aplicó a la sesión. Solución: cerrar Ubuntu, ejecutar `wsl --shutdown` en PowerShell y volver a abrirlo.
- Para pegar en la terminal de Ubuntu: `Ctrl + Shift + V` o clic derecho con nada seleccionado.
- Si algo falla, pedir el mensaje de error completo o una captura antes de proponer soluciones.
