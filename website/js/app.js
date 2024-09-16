document.addEventListener("DOMContentLoaded", () => {
  class Whiterose {
    constructor() {
      this.clockElement = document.getElementById("clock");
      this.tickSound = null;
      this.lastUpdate = -1;
      this.audioEnabled = false;
      this.audioToggle = document.getElementById("audioToggle");
      this.audioToggle.textContent = "🔇";
      this.updateInterval = 15;
      this.updateIntervalInput = document.getElementById("updateInterval");
      this.setIntervalButton = document.getElementById("setInterval");
      this.themeToggle = document.getElementById("themeToggle");
      this.timeFormatToggle = document.getElementById("timeFormatToggle");
      this.use24HourFormat = false;

      this.setupEventListeners();
      this.start();
      this.loadSavedSettings();
    }

    setupEventListeners() {
      document.addEventListener("visibilitychange", () => this.handleVisibilityChange());
      this.audioToggle.addEventListener("click", () => this.toggleAudio());
      this.setIntervalButton.addEventListener("click", () => this.setCustomInterval());
      document.getElementById("settingsIcon").addEventListener("click", () => this.openSettingsModal());
      document.querySelector(".close").addEventListener("click", () => this.closeSettingsModal());
      window.addEventListener("click", (event) => this.closeModalOnOutsideClick(event));
      this.themeToggle.addEventListener("change", () => this.switchTheme());
      this.timeFormatToggle.addEventListener("change", () => this.switchTimeFormat());
    }

    async loadTickSound() {
      try {
        const response = await fetch("./audio/tick.mp3");
        const arrayBuffer = await response.arrayBuffer();
        this.tickSound = await this.audioContext.decodeAudioData(arrayBuffer);
        console.debug("Tick sound loaded successfully");
      } catch (error) {
        console.error("Error loading tick sound:", error);
      }
    }

    start() {
      this.update();
      this.updateInterval = setInterval(() => this.update(), 1000);
    }

    update() {
      const now = new Date();
      let hours = now.getHours();
      const minutes = now.getMinutes().toString().padStart(2, "0");
      const seconds = now.getSeconds().toString().padStart(2, "0");

      if (!this.use24HourFormat) {
        hours = hours % 12 || 12;
        // Remove leading zero for 12-hour format, except for 12 (noon/midnight)
        hours = hours === 12 ? "12" : hours.toString().padStart(1, " ");
      } else {
        hours = hours.toString().padStart(2, "0");
      }

      this.clockElement.textContent = `${hours}:${minutes}:${seconds}`;

      const currentMinutes = now.getHours() * 60 + now.getMinutes();
      const currentSeconds = now.getSeconds();

      if (currentSeconds === 0 && (currentMinutes % this.updateInterval === 0) && currentMinutes !== this.lastUpdate) {
        this.lastUpdate = currentMinutes;
        this.speakTime(hours, minutes);
      } else {
        this.playTickSound();
      }
    }

    playTickSound() {
      const now = new Date();
      if (now.getSeconds() === 0 && this.audioEnabled && this.tickSound && this.audioContext && this.audioContext.state === "running") {
        const source = this.audioContext.createBufferSource();
        source.buffer = this.tickSound;
        source.connect(this.audioContext.destination);
        console.debug("Playing tick sound");
        source.start();
      }
    }

    speakTime(hours, minutes) {
      if (this.audioEnabled) {
        const timeString = this.formatTimeForSpeech(hours, minutes);
        console.debug("Speaking time:", timeString);
        const utterance = new SpeechSynthesisUtterance(timeString);
        utterance.rate = 0.9;
        utterance.pitch = 1.0;
        
        const voices = speechSynthesis.getVoices();
        const preferredVoices = [
          "Google UK English Female",
          "Microsoft Hazel Desktop - English (Great Britain)",
          "Microsoft George - English (United Kingdom)",
          "en-GB-Standard-A",
          "en-GB-Standard-B"
        ];
        
        for (const voiceName of preferredVoices) {
          const voice = voices.find((v) => v.name === voiceName);
          if (voice) {
            utterance.voice = voice;
            break;
          }
        }
        
        if (!utterance.voice) {
          utterance.voice = voices.find((voice) => voice.lang === "en-GB") || voices[0];
        }
        
        speechSynthesis.speak(utterance);
      }
    }

    formatTimeForSpeech(hours, minutes) {
      const h = parseInt(hours);
      const m = parseInt(minutes);
      let timeString = "It is ";

      if (this.use24HourFormat) {
        timeString += `${h} ${this.formatMinutes(m)}`;
      } else {
        timeString += `${h === 0 ? 12 : h > 12 ? h - 12 : h} ${this.formatMinutes(m)} ${h >= 12 ? "PM" : "AM"}`;
      }

      return timeString;
    }

    formatMinutes(minutes) {
      if (minutes === 0) {
        return "";
      } else if (minutes < 10) {
        return `oh ${minutes}`;
      } else {
        return minutes.toString();
      }
    }

    toggleAudio() {
      this.audioEnabled = !this.audioEnabled;
      this.audioToggle.textContent = this.audioEnabled ? "🔊" : "🔇";
      if (this.audioEnabled) {
        if (!this.audioContext) {
          this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
        }
        this.audioContext.resume().then(() => {
          console.debug("AudioContext resumed successfully");
          if (!this.tickSound) {
            this.loadTickSound();
          }
        });
      } else {
        if (this.audioContext) {
          this.audioContext.suspend();
        }
      }
    }

    handleVisibilityChange() {
      if (document.hidden) {
        clearInterval(this.updateInterval);
      } else {
        this.updateInterval = setInterval(() => this.update(), 1000);
      }
    }

    setCustomInterval() {
      const newInterval = parseInt(this.updateIntervalInput.value);
      if (newInterval >= 1 && newInterval <= 60) {
        this.updateInterval = newInterval;
        console.debug(`Update interval set to ${this.updateInterval} minutes`);
        this.closeSettingsModal();
      } else {
        console.error("Invalid interval. Please enter a number between 1 and 60.");
      }
    }

    openSettingsModal() {
      document.getElementById("settingsModal").style.display = "block";
    }

    closeSettingsModal() {
      document.getElementById("settingsModal").style.display = "none";
    }

    closeModalOnOutsideClick(event) {
      const modal = document.getElementById("settingsModal");
      if (event.target === modal) {
        modal.style.display = "none";
      }
    }

    switchTheme() {
      const theme = this.themeToggle.value;
      document.body.classList.remove("cream-theme", "dark-theme");
      if (theme === "cream") {
        document.body.classList.add("cream-theme");
      } else if (theme === "dark") {
        document.body.classList.add("dark-theme");
      }
      localStorage.setItem("theme", theme);
    }

    switchTimeFormat() {
      this.use24HourFormat = this.timeFormatToggle.value === "24";
      localStorage.setItem("timeFormat", this.use24HourFormat ? "24" : "12");
      this.update();
    }

    loadSavedSettings() {
      const savedTheme = localStorage.getItem("theme") || "white";
      this.themeToggle.value = savedTheme;
      this.switchTheme();

      const savedTimeFormat = localStorage.getItem("timeFormat") || "12";
      this.timeFormatToggle.value = savedTimeFormat;
      this.switchTimeFormat();
    }
  }

  new Whiterose();
});
