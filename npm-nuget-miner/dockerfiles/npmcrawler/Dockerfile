FROM python:3.8.2-alpine3.11	

ENV PYTHONUNBUFFERED=1

RUN pip install neo4j
RUN pip install requests
RUN pip install ijson

COPY ./dependencygraphutils/ /usr/app/npm_crawler/dependencygraphutils
COPY ./npmanalysis/ /usr/app/npm_crawler/npmanalysis
COPY ./npmentry.py /usr/app/npm_crawler/npmentry.py

WORKDIR /usr/app/npm_crawler

ENTRYPOINT python /usr/app/npm_crawler/npmentry.py
