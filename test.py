import pandas as pd
df = pd.read_csv("소득2006.csv")
pd.set_option('display.max_columns',None)
df.head()
