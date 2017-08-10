import yaml
import datetime

def load_configs_params(config_yml_path, params_yml_path):
    with open(config_yml_path, 'r') as config_ymlfile:
        l2v_cfg = yaml.load(config_ymlfile)
    with open(params_yml_path, 'r') as params_ymlfile:
        l2v_params = yaml.load(params_ymlfile)
    return l2v_cfg, l2v_params


def write_LLR_cli(l2v_params, l2v_cfg):
    today = datetime.datetime.now().strftime("%m%d%y")

    llr_JAR = l2v_cfg['PATHS']["JARS"] + l2v_cfg['JARS']["LLR_JAR"]
    jar_serialization = l2v_cfg['JARS']['LLR_JAR'].replace("-assembly-","").replace(".jar", "").replace(".","")
    llr_params_serialization = l2v_params['LLR']['options'][0] + l2v_params['LLR']['useroritem'][0] + str(l2v_params['LLR']['threshold']).replace(".","")
    output_folder = jar_serialization + "-" + today +  "-" + llr_params_serialization
    output_for_llr = l2v_cfg['PATHS']["OUTPUT"] + "llr_output/" + output_folder
    LLR_EMR = """spark-submit --deploy-mode cluster --class llr.LLR {} --master yarn --options {} --useroritem {} --threshold {} --interactionsFile {} --outputFile {} --separator "," --maxInteractionsPerUserOrItem 500 --seed 12345""".format(llr_JAR, l2v_params['LLR']['options'], l2v_params['LLR']['useroritem'], l2v_params['LLR']['threshold'], l2v_cfg['DATA']['TRAIN'], output_for_llr )

    return LLR_EMR, output_for_llr, llr_params_serialization


def write_EMB_cli(l2v_params, l2v_cfg, output_for_llr, llr_params_serialization):
    today = datetime.datetime.now().strftime("%m%d%y")

    input_embeddings = output_for_llr + "/part-00000"
    ne_jar = l2v_cfg['JARS']["EMBEDDINGS_JAR"].replace("n2v-assembly-","").replace(".","").replace("jar","")
    embs = "d{}w{}l{}n{}d{}-".format(l2v_params['EMBEDDINGS']['dim'], l2v_params['EMBEDDINGS']['window'], l2v_params['EMBEDDINGS']['walkLength'], l2v_params['EMBEDDINGS']['numWalks'] , l2v_params['EMBEDDINGS']['degree'])
    n2v = "p{}q{}".format(l2v_params['EMBEDDINGS']['p'], l2v_params['EMBEDDINGS']['q'])

    ne_output_folder = "embeddings" + ne_jar + "-" + llr_params_serialization + "-" + today + "-" + embs + n2v
    output_for_embeddings = l2v_cfg['PATHS']["OUTPUT"] + "network-embeddings/" + ne_output_folder

    embedding_JAR = l2v_cfg['PATHS']["JARS"] + l2v_cfg['JARS']["EMBEDDINGS_JAR"]

    embeddings_d = l2v_params['EMBEDDINGS']["dim"]

    w = l2v_params['EMBEDDINGS']["window"]
    l = l2v_params['EMBEDDINGS']["walkLength"]
    n = l2v_params['EMBEDDINGS']["numWalks"]
    de = l2v_params['EMBEDDINGS']["degree"]
    p = l2v_params['EMBEDDINGS']["p"]
    q = l2v_params['EMBEDDINGS']["q"]

    network_embeddings_EMR = """spark-submit --deploy-mode cluster --class Main {} --dim {} --window {} --walkLength {} --numWalks {} --degree {} --p {} --q {} --weighted true --directed false --indexed true --input {} --output {} --cmd node2vec""".format(embedding_JAR, embeddings_d, w, l, n, de, p, q, input_embeddings, output_for_embeddings )

    return network_embeddings_EMR, output_for_embeddings, embs, n2v, embeddings_d


def write_PRED_cli(l2v_params, l2v_cfg, output_for_embeddings, llr_params_serialization, embs, n2v, embeddings_d):
    today = datetime.datetime.now().strftime("%m%d%y")

    prediction_JAR = l2v_cfg["PATHS"]["JARS"] + l2v_cfg["JARS"]["PREDICTIONS_JAR"]
    p_ntype = l2v_params["PREDICTIONS"]["ntype"]
    p_neighbors = l2v_params["PREDICTIONS"]["neighbors"]
    emb_path = output_for_embeddings + ".emb" + "/part-00000"
    p_output = "-" + str(p_neighbors) + "-" + today
#     p_output_folder = llr_params_serialization + "-" + embs[:4] + n2v + "-" + str(p_neighbors) + "-" + today
    p_output_folder = llr_params_serialization + "-" + embs + n2v + "-" + str(p_neighbors) + "-" + today
    prediction_path = l2v_cfg["PATHS"]["OUTPUT"] + "predictions/" + p_output_folder
    rmse_path = l2v_cfg["PATHS"]["OUTPUT"] + "rmse/" + p_output_folder
    prediction_EMR = """spark-submit --deploy-mode cluster --class Prediction --master yarn-cluster {} --dim {} --ntype {} --train {} --test {} --embedding {} --neighbors {} --rmse {} --predictions {}""".format(prediction_JAR, embeddings_d, p_ntype, l2v_cfg["DATA"]["TRAIN"], l2v_cfg["DATA"]["VALIDATION"], emb_path, p_neighbors, rmse_path, prediction_path)
    return prediction_EMR

# TODO: Create function for evaluation

def params_to_cli(path_to_l2v_config, path_to_l2v_params):
    # load params
    l2v_cfg, l2v_params = load_configs_params(path_to_l2v_config, path_to_l2v_params)
    # llr command
    llr, output_folder_LLR, llr_params_serialization = write_LLR_cli(l2v_params, l2v_cfg)
    # embeddings command
    emb, output_for_embeddings, embs, n2v, embeddings_d = write_EMB_cli(l2v_params, l2v_cfg, output_folder_LLR, llr_params_serialization)
    # prediction command
    pred = write_PRED_cli(l2v_params, l2v_cfg, output_for_embeddings, llr_params_serialization, embs, n2v, embeddings_d)
    # evaluation command
    # eval =

    return llr, emb, pred

def create_steps(llr=None, emb=None, pred=None, name=''):
    if llr != None:
        Steps=[

        {
            'Name': name + '-LLR',
            'ActionOnFailure': 'CONTINUE',
            'HadoopJarStep': {
                'Jar': 'command-runner.jar',
                'Args': (llr).split(),
            }
        },
        {
            'Name': name + '-EMB',
            'ActionOnFailure': 'CONTINUE',
            'HadoopJarStep': {
                'Jar': 'command-runner.jar',
                'Args': (emb).split(),
            }
        },
        {
            'Name': name + '-PRED',
            'ActionOnFailure': 'CONTINUE',
            'HadoopJarStep': {
                'Jar': 'command-runner.jar',
                'Args': (pred).split(),
            }
        }
    ]
    else:
        Steps=[
        {
            'Name': name + '-EMB',
            'ActionOnFailure': 'CONTINUE',
            'HadoopJarStep': {
                'Jar': 'command-runner.jar',
                'Args': (emb).split(),
            }
        },
        {
            'Name': name + '-PRED',
            'ActionOnFailure': 'CONTINUE',
            'HadoopJarStep': {
                'Jar': 'command-runner.jar',
                'Args': (pred).split(),
            }
        }
    ]


    return Steps


if __name__ == '__main__':
    llr, emb, pred = params_to_cli("CONFIGS/ex1-ml-1m-config.yml", "CONFIGS/ex3420-du05d100w10l80n10d30p5q1-900-072717-params.yml")
