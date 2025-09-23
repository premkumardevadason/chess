#!/usr/bin/env python3
import urllib.request
import os

# Download OpenCV Haar cascade file
cascade_url = "https://raw.githubusercontent.com/opencv/opencv/master/data/haarcascades/haarcascade_frontalface_alt.xml"
cascade_file = "haarcascade_frontalface_alt.xml"

print(f"Downloading {cascade_file}...")
urllib.request.urlretrieve(cascade_url, cascade_file)
print(f"Downloaded {cascade_file} successfully!")

# Also download the default frontal face cascade as backup
default_url = "https://raw.githubusercontent.com/opencv/opencv/master/data/haarcascades/haarcascade_frontalface_default.xml"
default_file = "haarcascade_frontalface_default.xml"

print(f"Downloading {default_file}...")
urllib.request.urlretrieve(default_url, default_file)
print(f"Downloaded {default_file} successfully!")