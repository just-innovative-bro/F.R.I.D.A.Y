import sys
from gtts import gTTS  # pip install gTTS
import requests
import speech_recognition as sr  # pip install speechRecognition
import datetime
import cv2  # pip install opencv-python
import random
from requests import get
import wikipedia  # pip install wikipedia
import webbrowser  # pip install web browser
import smtplib  # pip install smtplib
import pyjokes  # pip install pyjokes
import pyautogui  # pip install pyautogui
import psutil  # pip install psutil
import PyPDF2  # pip install PyPDF2
from pywikihow import search_wikihow  # pip install pywikihow
from PyQt5 import QtGui  # pip install PyQt5
from PyQt5.QtCore import QThread
from PyQt5.QtWidgets import QMainWindow, QApplication
from FridayUI import UiFriday  # from the UI program
import audioplayer  # pip install audioplayer
import wolframalpha  # pip install wolframalpha
from neuralintents import GenericAssistant
from quote import quote


class Person:
    """This class is to save username and return name when needed"""

    name = ""

    def rename(self, name):
        """Declaring"""
        self.name = name


class Friday:
    """This class is to save assistant name and return name when needed"""

    name = ""

    def rename(self, name):
        """Declaring"""
        self.name = name


person_obj = Person()
friday_obj = Friday()
Friday.name = ""
person_obj.name = ""


def speak(audio):
    """Google text-to-speech function"""
    tts = gTTS(audio)
    tts.save("friday.mp3")
    audioplayer.AudioPlayer("friday.mp3").play(block=True)


def wish():
    """Wish with respective datetime"""
    hour = int(datetime.datetime.now().hour)

    if 8 <= hour < 12:
        speak("good morning")
    elif 13 <= hour < 16:
        speak("good afternoon")
    else:
        speak("good evening")
    speak("i am friday sir. please tell me how can i help you")


def jokes():
    """tells joke"""
    joke = pyjokes.get_joke()
    speak(joke)


def email(self):
    """Sending email through voice"""
    speak("What should i say")
    self.query = self.voicecom().lower()

    gmail = input("Enter your email address: ")
    password = input("Enter your password: ")
    send_to_person = input("Enter the receiver email address:")
    message = self.query

    server = smtplib.SMTP("smtp.gmail.com", 587)
    server.starttls()
    server.login(gmail, password)
    server.sendmail(gmail, send_to_person, message)
    server.quit()
    speak(f"email has been sent to {send_to_person}")


def quotes():
    """daily quotes"""
    search = "Jasper Fforde"
    result = quote(search, limit=1)
    print(result)
    speak(result)


def news():
    """News text from api"""
    # paste your key in the dash
    main_url = "https://newsapi.org/v2/top-headlines?sources=techcrunch&apiKey=f6c14e2cab554378974887255cff7a09"
    main_page = requests.get(main_url).json()
    articles = main_page["articles"]
    head = []
    day = [
        "fist",
        "second",
        "third",
        "fourth",
        "five",
        "six",
        "seven",
        "eight",
        "ninth",
        "tenth",
    ]
    for ar in articles:
        head.append(ar["title"])
    for i in range(len(day)):
        speak(f"today's {day[i]} news is: {head[i]}")


def days():
    """Returns present day"""
    day = datetime.datetime.today().weekday() + 1

    day_dict = {
        1: "Monday",
        2: "Tuesday",
        3: "Wednesday",
        4: "Thursday",
        5: "Friday",
        6: "Saturday",
        7: "Sunday",
    }

    if day in day_dict:
        day_of_the_week = day_dict[day]
        print(day_of_the_week)
        speak("The day is " + day_of_the_week)


def date():
    """Returns present date"""
    year = int(datetime.datetime.now().year)
    month = int(datetime.datetime.now().month)
    date1 = int(datetime.datetime.now().day)
    speak("the current date is")
    speak(date1)
    speak(month)
    speak(year)


def screenshot():
    """Takes snapshot"""
    img = pyautogui.screenshot()
    img.save("screen.png")


def ask(self):
    """Solve"""
    speak("I can answer to computational questions")
    question = self.voicecom().lower()
    app_id = "QTLYH7-4UV274RV6V"
    client = wolframalpha.Client(app_id)
    res = client.query(question)
    answer = next(res.results).text

    speak(answer)
    print(answer)


def calender():
    """Opens link"""
    url = "https://calendar.google.com/calendar/u/0/r?tab=rc"
    webbrowser.get().open(url)


def note(self):
    """Writes voice note"""
    speak("What should i write, sir")
    notes = self.voicecom()
    with open("friday.txt", "w") as file:
        file.write(notes)
        file.close()


def wiki(self):
    """Summary search"""
    speak("searching wikipedia")
    self.query = self.query.replace("wikipedia")
    result = wikipedia.summary(self.query, sentence=2)
    speak("according to wikipedia:")
    speak(result)
    print(result)


def read_note():
    """Voice note"""
    speak("Showing Notes")
    with open("friday.txt", "r") as file:
        read = file.read()
        speak(read)


def cmd():
    """opens command promt"""
    import os
    import shlex
    os.system(shlex.quote(cmd))


def ip():
    """Returns Internet Protocol address"""
    ips = get("https://api.ipify.org").text
    speak(f"your ip address is {ips}")


def cpu():
    """Stats"""
    usage = str(psutil.cpu_percent())
    speak("CPU is at" + usage)
    battery = psutil.sensors_battery()

    plugged = battery.power_plugged
    percent = str(battery.percent)
    plugged = "Plugged In" if plugged else "Not Plugged In"
    speak("battery is at ")
    speak(percent + "% | " + plugged)


def cam():
    """Opens front webcam"""
    video_capture = cv2.VideoCapture(0)

    cv2.namedWindow("Face cam")

    while True:
        _ret, frame = video_capture.read()
        cv2.imshow("Face cam", frame)

        # This breaks on 'q' key
        if cv2.waitKey(1) & 0xFF == ord("q"):
            break

    video_capture.release()
    cv2.destroyAllWindows()


def stock(self):
    """Market search google"""
    search_term = self.query.split("for")[-1]
    url = "https://google.com/search?q=" + search_term
    webbrowser.get().open(url)
    speak("Here is what I found for " + search_term + " on google")


def pdf_reader():
    """Reader"""
    book = open(input("enter path to pdf:"), "rb")
    pdfreader = PyPDF2.PdfFileReader(book)
    pages = pdfreader.getNumPages()
    speak(f"total number of pages in this book {pages}")
    speak("please enter a page number from 0 to something i have to read")
    pg = int(input("enter here:"))
    page = pdfreader.getPage(pg)
    text = page.extractText()
    speak(text)


def time_now():
    """tells time"""
    stripe = datetime.datetime.now().strftime("%H:%M")
    speak(f"Sir, the time is {stripe}")


def support():
    """Software contact link"""
    url = "https://github.com/https-github-com-zameel28/F.R.I.D.A.Y"
    webbrowser.get().open(url)


def youtube(self):
    """search youtube video"""
    search_term = self.query.split("for")[-1]
    search_term = search_term.replace("open youtube", "").replace("search", "")
    url = "https://www.youtube.com/results?search_query=" + search_term
    webbrowser.get().open(url)
    speak("Here is what I found for " + search_term + "on youtube")


def google(self):
    """google search"""
    speak("sir, what should i search")
    search_term = self.query.split("to")[-1]
    url = "https://www.google.co.in/search?q=" + search_term
    webbrowser.get().open(url)


def route_to(self):
    """google map routing"""
    search_term = self.query.split("to")[-1]
    url = "https://www.google.co.in/maps/dir/" + search_term
    webbrowser.get().open(url)
    speak("Here is what I found for" + search_term + "on google maps")


def wiki_step_mode(self):
    """step by step process"""
    speak("activated how to do mode")
    how = self.voicecom()
    max_result = 1
    how_to = search_wikihow(how, max_result)
    if len(how_to) != 1:
        raise AssertionError
    how_to[0].print()
    speak(how_to[0].summary)


def setup():
    """Start up audio"""
    list1 = ["Ironman Airborne.mp3", "Iron Man Music.mp3", "Iron Man.mp3"]
    audioplayer.AudioPlayer(random.choice(list1)).play(block=True)


def intro():
    """Introduction audio"""
    list2 = ["Ironman Airborne.mp3", "Iron Man Music.mp3", "Iron Man.mp3"]
    audioplayer.AudioPlayer(random.choice(list2)).play(block=True)


mappings = {
    "calendar": calender,
    "write_note": note,
    "greetings": wish,
    "tell_date": date,
    "open_cam": cam,
    "support": support,
    "setup": setup,
    "into": intro,
    "wish": wish,
    "pdf_reader": pdf_reader,
    "jokes": jokes,
    "quote": quotes,
    "open_cmd": cmd,
    "news": news,
}
assistant = GenericAssistant("intents.json", intent_methods=mappings)
assistant.train_model()


class MainThread(QThread):  # main
    """Main class"""

    def __init__(self, parent=None):
        """self.query field"""
        super().__init__(parent)
        self.query = None

    def run(self):
        """Running thread"""
        self.main()

    @staticmethod
    def voicecom():  # speech-to-text
        """Recognize"""
        r = sr.Recognizer()
        with sr.Microphone() as source:
            print("Listening...")
            r.pause_threshold = 1
            audio = r.listen(source)

        try:
            print("Recognizing...")
            query = r.recognize_google(audio, language="en-in")
            print(f"User said: {query}\n")

        except Exception as e:
            print(e)
            print("Say that again please...")
            return "None"
        return query

    def null(self):
        """Trigger function"""
        while True:
            self.query = self.voicecom().lower()
            if "friday" in self.query:
                speak("ready")
                break

    def main(self):  # main task execution
        """The original task"""
        wish()
        while True:
            self.query = self.voicecom().lower()
            assistant.request(self.query)


startExecution = MainThread()


class Main(QMainWindow):
    """Program gui thread"""

    def __init__(self):
        super().__init__()
        self.ui = UiFriday()
        self.ui.setup(self)
        self.ui.pushButton.clicked.connect(self.start)
        self.ui.pushButton_2.clicked.connect(self.close)

    def start(self):
        """Background gif play"""
        self.ui.movie = QtGui.QMovie("untitled-6.gif")
        self.ui.label.setMovie(self.ui.movie)
        self.ui.movie.start()
        startExecution.start()


# the UI


app = QApplication(sys.argv)
friday = Main()
friday.show()
sys.exit(app.exec_())
