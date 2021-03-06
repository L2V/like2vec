# like2vec
Like2Vec is a neaborhood based recommendation algorithm that uses network embeddings to create latent representations of transactional history

![like2vec](https://raw.githubusercontent.com/L2V/like2vec/master/reports/figures/l2v-components.png "Pipeline components")

The components of the pipeline are:                      
+ [Utility Matrix - LLR](https://github.com/L2V/like2vec/wiki/Utility-Matrix---LLR)                  
+ [Network Embeddings](https://github.com/L2V/like2vec/wiki/Network-Embeddings)                
+ [Prediction](https://github.com/L2V/like2vec/wiki/Prediction)                 
+ [Evaluation](https://github.com/L2V/like2vec/wiki/Evaluation)            


## Folder Structure
```
├── LICENSE
├── README.md          <- The top-level README for developers using this project.
|
├── docs               <- A default Sphinx project; see sphinx-doc.org for details
|
├── notebooks          <- Jupyter notebooks. Naming convention is a number (for ordering),
│                         the creator's initials, and a short `-` delimited description, e.g.
│                         `1.0-jja-initial-data-exploration`.
│
├── references         <- Data dictionaries, manuals, and all other explanatory materials.
│
├── reports            <- Generated analysis as HTML, PDF, LaTeX, etc.
│   └── figures        <- Generated graphics and figures to be used in reporting
│
├── src                <- Source code for project.
│   ├── embeddings     <- Module to create network embeddings.
│   │
│   ├── llr            <- Module to calculate log-likelihood ratio of transactions
│   │
│   ├── prediction     <- Module to create neighborhood-based predictions
│   │   
│   ├── evaluation     <- Module to evaluate predictions
│       
```
